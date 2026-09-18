#!/usr/bin/env bash
# Download the last successful cloud snapshot and restore to a disposable DB.
set -Eeuo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/scripts/backup-common.sh"
load_restic_config
exec 9>"$BACKUP_ROOT/.backup.lock"
flock -w 120 9
work=$(mktemp -d "$BACKUP_ROOT/.restore-test-XXXXXXXX")
test_container=''
cleanup() {
  local rc=$?
  trap - EXIT
  if [[ -n "$test_container" ]]; then docker rm -fv "$test_container" >/dev/null || true; fi
  rm -rf -- "$work"
  exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
snapshot=$(jq -er '.snapshot | select(test("^[0-9a-f]{8,64}$"))' "$BACKUP_ROOT/last-cloud-success.json")
source_path=$(jq -er '.source' "$BACKUP_ROOT/last-cloud-success.json")
[[ "${source_path%/*}" == "$BACKUP_ROOT" && "${source_path##*/}" =~ ^daily-[0-9]{8}-[0-9]{6}-[0-9]{9}$ ]]
restic restore "$snapshot" --target "$work/restored"
restored="$work/restored$source_path"
[[ -f "$restored/.managed-mangashelf-backup-v1" && ! -L "$restored/.managed-mangashelf-backup-v1" ]]
[[ $(cat "$restored/.managed-mangashelf-backup-v1") == mangashelf-backup-v1 ]]
(cd "$restored" && sha256sum --strict -c SHA256SUMS)
tar -tzf "$restored/covers.tar.gz" >/dev/null
cd "$PROJECT_DIR"
db_id=$(docker compose ps -q db)
[[ -n "$db_id" ]]
db_image=$(docker inspect --format '{{.Image}}' "$db_id")
test_container=$(docker run -d --network none \
  --mount type=volume,destination=/var/lib/postgresql \
  -e POSTGRES_HOST_AUTH_METHOD=trust \
  -e POSTGRES_USER=restorecheck -e POSTGRES_DB=restorecheck "$db_image")
ready=0
for ((i=0;i<60;i++)); do
  if docker exec "$test_container" pg_isready -h 127.0.0.1 -U restorecheck -d restorecheck >/dev/null 2>&1; then ready=1; break; fi
  sleep 2
done
[[ "$ready" == 1 ]] || { echo 'Temporary DB did not become ready.' >&2; docker logs "$test_container" >&2; exit 1; }
docker exec -i "$test_container" pg_restore --exit-on-error --no-owner --no-privileges \
  -U restorecheck -d restorecheck < "$restored/database.dump"
docker exec "$test_container" psql -X -v ON_ERROR_STOP=1 -U restorecheck -d restorecheck \
  -c 'SELECT count(*) AS restored_migrations FROM flyway_schema_history WHERE success;'
jq -n --arg at "$(date --iso-8601=seconds)" --arg snapshot "$snapshot" \
  '{completed_at:$at,snapshot:$snapshot,checks:["cloud_download","sha256","covers_archive","postgresql_restore"]}' > "$work/success.json"
mv -- "$work/success.json" "$BACKUP_ROOT/last-restore-test.json"
echo 'Cloud recovery and isolated PostgreSQL restore verified.'
