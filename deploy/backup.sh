#!/usr/bin/env bash
set -euo pipefail
umask 077
root=/opt/petroad
exec 9>"$root/deploy.lock"
flock -w 300 9
release=$(readlink -f "$root/current")
file="$root/backups/$(date -u +%Y%m%dT%H%M%SZ)-manual.dump"
docker compose -p petroad --env-file "$root/secrets/server.env" --env-file "$release/image.env" \
  -f "$release/deploy/compose.yml" exec -T db pg_dump -U postgres -Fc petroad > "$file"
test -s "$file"
echo "$file"
