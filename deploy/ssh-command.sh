#!/usr/bin/env bash
set -euo pipefail
read -r verb sha run_id digest actor extra <<< "${SSH_ORIGINAL_COMMAND:-}"
if [[ $verb != deploy || -n ${extra:-} || ! ${sha:-} =~ ^[0-9a-f]{40}$ || ! ${run_id:-} =~ ^[1-9][0-9]{0,17}$ || ! ${digest:-} =~ ^sha256:[0-9a-f]{64}$ || ! ${actor:-} =~ ^[a-zA-Z0-9_-]+$ ]]; then
  echo 'Only the PetRoad deployment command is permitted.' >&2
  exit 2
fi
exec sudo -n /usr/local/sbin/petroad-deploy "$sha" "$run_id" "$digest" "$actor"
