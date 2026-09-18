#!/usr/bin/env bash
# Local backup, with the same layout/retention/marker contract as Kutt.
set -Eeuo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/scripts/backup-common.sh"
[[ $# == 0 ]] || { echo 'Usage: ./backup.sh (optional MANGASHELF_BACKUP_ROOT)' >&2; exit 2; }
retention=${MANGASHELF_LOCAL_RETENTION_DAYS:-14}
[[ "$retention" =~ ^[1-9][0-9]{0,3}$ ]] || { echo 'Invalid local retention.' >&2; exit 2; }
exec 9>"$BACKUP_ROOT/.backup.lock"
flock -n 9 || { echo 'Another backup or cloud operation is running.' >&2; exit 1; }
stage=''
marker=''
cleanup() {
  local rc=$?
  trap - EXIT
  if [[ -n "$stage" ]]; then rm -rf -- "$stage"; fi
  if [[ -n "$marker" ]]; then rm -f -- "$marker"; fi
  if (( rc != 0 )); then echo "Local backup FAILED (exit $rc). See the journal." >&2; fi
  exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
cd -- "$PROJECT_DIR"
stage=$(mktemp -d "$BACKUP_ROOT/.partial-XXXXXXXX")
files=(.env docker-compose.yml)
for file in compose.override.yml compose.override.yaml docker-compose.override.yml docker-compose.override.yaml; do
  if [[ -e "$file" ]]; then files+=("$file"); fi
done
for file in "${files[@]}"; do
  [[ -f "$file" && ! -L "$file" ]]
  install -m 600 -- "$file" "$stage/$file"
done

# Keep the existing low-level backup/restore format compatible.
if "$PROJECT_DIR/scripts/backup.sh" "$stage/data" > "$stage/backup.log" 2>&1; then
  :
else
  rc=$?
  cat "$stage/backup.log" >&2
  exit "$rc"
fi
source_dir=$(sed -n 's/^Backup completed: //p' "$stage/backup.log")
[[ "${source_dir%/*}" == "$stage/data" && -d "$source_dir" ]]
for file in database.dump covers.tar.gz manifest.txt; do mv -- "$source_dir/$file" "$stage/$file"; done
rm -rf -- "$stage/data"
rm -- "$stage/backup.log"
for file in "${files[@]}"; do
  cmp -s -- "$file" "$stage/$file" || { echo 'Configuration changed during backup; retry after deploy.' >&2; exit 1; }
done

# Image metadata only: never save docker inspect's container environment.
for service in db backend frontend; do
  image=$(docker compose images -q "$service")
  [[ -n "$image" && "$image" != *$'\n'* ]]
  printf '%s %s\n' "$service" "$image" >> "$stage/image-ids.txt"
  printf '%s ' "$service" >> "$stage/image-digests.txt"
  docker image inspect --format '{{json .RepoDigests}}' "$image" >> "$stage/image-digests.txt"
done
printf 'mangashelf-backup-v1\n' > "$stage/.managed-mangashelf-backup-v1"
(
  cd -- "$stage"
  sha256sum database.dump covers.tar.gz manifest.txt "${files[@]}" \
    image-ids.txt image-digests.txt .managed-mangashelf-backup-v1 > SHA256SUMS
  sha256sum --strict -c SHA256SUMS
)
final="$BACKUP_ROOT/daily-$(date +%Y%m%d-%H%M%S-%N)"
[[ ! -e "$final" ]]
mv -T -- "$stage" "$final"
stage=''
marker=$(mktemp "$BACKUP_ROOT/.last-success-XXXXXXXX")
printf '%s\n' "$final" > "$marker"
mv -f -- "$marker" "$BACKUP_ROOT/last-success"
marker=''
printf 'Backup completed: %s\n' "$final"

# Exactly the Kutt policy: at least 14 elapsed days, only marked direct children.
while IFS= read -r -d '' old; do
  name=${old##*/}
  if [[ "$name" =~ ^daily-[0-9]{8}-[0-9]{6}-[0-9]{9}$ && "$old" != "$final" &&
        -f "$old/.managed-mangashelf-backup-v1" && ! -L "$old/.managed-mangashelf-backup-v1" ]]; then
    [[ $(cat "$old/.managed-mangashelf-backup-v1") == mangashelf-backup-v1 ]] || continue
    rm -rf -- "$old"
    printf 'Expired local backup removed: %s\n' "$name"
  fi
done < <(find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -name 'daily-*' -mtime +"$((retention - 1))" -print0)
