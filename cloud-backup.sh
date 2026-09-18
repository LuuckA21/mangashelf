#!/usr/bin/env bash
# Runs as ExecStartPost, only after a successful local backup, like Kutt.
set -Eeuo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/scripts/backup-common.sh"
load_restic_config
for cmd in restic jq flock sha256sum; do command -v "$cmd" >/dev/null; done
exec 9>"$BACKUP_ROOT/.backup.lock"
flock -w 120 9
work=''
cleanup() {
  local rc=$?
  trap - EXIT
  if [[ -n "$work" ]]; then rm -rf -- "$work"; fi
  if (( rc != 0 )); then echo "Cloud backup FAILED (exit $rc); local backup retained, cloud marker unchanged." >&2; fi
  exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
backup=$(latest_managed_backup)
for name in database.dump covers.tar.gz manifest.txt .env docker-compose.yml image-ids.txt image-digests.txt SHA256SUMS; do
  [[ -f "$backup/$name" && ! -L "$backup/$name" ]]
done
(cd "$backup" && sha256sum --strict -c SHA256SUMS)
work=$(mktemp -d "$BACKUP_ROOT/.cloud-work-XXXXXXXX")
restic backup "$backup" --host "${MANGASHELF_BACKUP_HOST:-$(hostname)}" --tag mangashelf \
  --group-by host,tags --json > "$work/backup.jsonl"
snapshot=$(jq -ers '[.[] | select(.message_type == "summary")][-1].snapshot_id | select(type == "string") | select(test("^[0-9a-f]{8,64}$"))' "$work/backup.jsonl")
restic forget --host "${MANGASHELF_BACKUP_HOST:-$(hostname)}" --tag mangashelf --group-by host,tags \
  --keep-daily 14 --keep-weekly 8 --keep-monthly 12 --prune
restic check
jq -n --arg at "$(date --iso-8601=seconds)" --arg source "$backup" --arg snapshot "$snapshot" \
  --arg repository "$RESTIC_REPOSITORY" \
  '{completed_at:$at,source:$source,snapshot:$snapshot,repository:$repository}' > "$work/success.json"
mv -f -- "$work/success.json" "$BACKUP_ROOT/last-cloud-success.json"
echo "Cloud backup, retention and repository check completed: $snapshot"
