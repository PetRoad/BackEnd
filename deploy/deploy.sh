#!/usr/bin/env bash
set -euo pipefail
umask 077
if [[ $# != 4 || ! $1 =~ ^[0-9a-f]{40}$ || ! $2 =~ ^[1-9][0-9]{0,17}$ || ! $3 =~ ^sha256:[0-9a-f]{64}$ || ! $4 =~ ^[a-zA-Z0-9_-]+$ ]]; then
  echo 'Usage: petroad-deploy COMMIT RUN_ID IMAGE_DIGEST REGISTRY_USER' >&2
  exit 2
fi
sha=$1
run_id=$2
image="ghcr.io/petroad/backend@$3"
registry_user=$4
root=/opt/petroad
exec 9>"$root/deploy.lock"
flock -w 300 9
if [[ -f $root/high-water-run ]] && (( run_id < $(cat "$root/high-water-run") )); then
  echo 'Skipped an older deployment run.'
  exit 0
fi
printf '%s\n' "$run_id" > "$root/high-water-run"
registry_config=$(mktemp -d)
trap 'rm -rf "$registry_config"' EXIT
IFS= read -r registry_token || true
if [[ -n ${registry_token:-} ]]; then
  printf '%s' "$registry_token" | docker --config "$registry_config" login ghcr.io --username "$registry_user" --password-stdin >/dev/null 2>&1
fi
unset registry_token
if (( $(df -PB1 "$root" | awk 'NR==2 {print $4}') < 5368709120 )); then
  echo 'At least 5 GiB of free disk is required before deployment.' >&2
  exit 1
fi
digest=${3#sha256:}
# Keep each image/run in a separate directory so a same-commit retry cannot overwrite rollback settings.
release="$root/releases/$sha-$run_id-$digest"
if [[ ! -f $release/deploy/compose.yml ]]; then
  archive=$(mktemp "$root/releases/.archive.XXXXXX")
  curl --fail --silent --show-error --location "https://codeload.github.com/PetRoad/BackEnd/tar.gz/$sha" -o "$archive"
  mkdir -p "$release"
  tar -xzf "$archive" --strip-components=1 --no-same-owner --no-same-permissions -C "$release"
  rm -f "$archive"
fi
chmod 755 "$release/deploy/initdb"
chmod 755 "$release/deploy/initdb/01-app-role.sh"
printf 'BACKEND_IMAGE=%s\nAPP_COMMIT=%s\n' "$image" "$sha" > "$release/image.env"
compose() {
  local directory=$1
  shift
  docker --config "$registry_config" compose -p petroad --env-file "$root/secrets/server.env" \
    --env-file "$directory/image.env" -f "$directory/deploy/compose.yml" "$@"
}
compose "$release" config --quiet
compose "$release" pull api
if [[ $(docker image inspect "$image" --format '{{.Architecture}}') != arm64 || $(docker image inspect "$image" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}') != "$sha" ]]; then
  echo 'Image architecture or commit label does not match the requested release.' >&2
  exit 1
fi
previous=$(readlink -f "$root/current" || true)
compose "$release" up -d --wait --wait-timeout 120 db
backup="$root/backups/$(date -u +%Y%m%dT%H%M%SZ)-before-$sha.dump"
compose "$release" exec -T db pg_dump -U postgres -Fc petroad > "$backup"
if [[ ! -s $backup ]]; then echo 'Database backup failed.' >&2; exit 1; fi

healthy() {
  local expected=$1
  local response
  response=$(curl --fail --silent --show-error --max-time 5 http://127.0.0.1:18080/api/health) || return 1
  printf '%s' "$response" | python3 -c 'import json,sys; d=json.load(sys.stdin); sys.exit(0 if d.get("status")=="ok" and d.get("commit")==sys.argv[1] else 1)' "$expected"
}
rollback() {
  echo 'New release failed; restoring the previous backend image.' >&2
  if [[ -n $previous && -f $previous/image.env ]]; then
    if compose "$previous" up -d --wait --wait-timeout 180 api; then
      previous_sha=$(sed -n 's/^APP_COMMIT=//p' "$previous/image.env")
      if healthy "$previous_sha"; then echo 'Previous backend is healthy; database was not reverted.' >&2
      else echo 'Rollback health check failed. Use the saved database backup and recovery procedure.' >&2; fi
    else echo 'Rollback failed. Database changes may require manual recovery from the saved backup.' >&2; fi
  else
    compose "$release" stop api || true
    echo 'No previous release exists; database and backup have been preserved.' >&2
  fi
  exit 1
}
if ! compose "$release" up -d --wait --wait-timeout 180 api; then rollback; fi
if ! healthy "$sha"; then rollback; fi
if ! compose "$release" up -d tunnel; then rollback; fi
public_ready=false
for attempt in {1..12}; do
  if response=$(curl --fail --silent --show-error --max-time 10 https://petroad-api.hrxlou.com/api/health); then
    if printf '%s' "$response" | python3 -c 'import json,sys; d=json.load(sys.stdin); sys.exit(0 if d.get("status")=="ok" and d.get("commit")==sys.argv[1] else 1)' "$sha"; then
      public_ready=true
      break
    fi
  fi
  sleep 5
done
if [[ $public_ready != true ]]; then rollback; fi
ln -sfn "$release" "$root/current"
if [[ -n $previous && $previous != "$release" ]]; then ln -sfn "$previous" "$root/previous"; fi
printf '%s\n' "$sha" > "$root/deployed-commit"
echo "Deployed commit $sha."
