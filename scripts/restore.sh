#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname -- "$SCRIPT_DIR")"

usage() {
  printf 'Usage: %s BACKUP_DIRECTORY [--yes] [--skip-safety-backup]\n' "${0##*/}"
  printf 'Destructively restores PostgreSQL and covers from a verified backup.\n'
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

if (( $# < 1 )); then
  usage >&2
  exit 2
fi

BACKUP_DIR_INPUT="$1"
shift
ASSUME_YES=false
SKIP_SAFETY_BACKUP=false

while (( $# > 0 )); do
  case "$1" in
    --yes) ASSUME_YES=true ;;
    --skip-safety-backup) SKIP_SAFETY_BACKUP=true ;;
    *)
      printf 'Error: unknown option: %s\n' "$1" >&2
      usage >&2
      exit 2
      ;;
  esac
  shift
done

command -v docker >/dev/null 2>&1 || {
  printf 'Error: docker is required.\n' >&2
  exit 1
}
command -v sha256sum >/dev/null 2>&1 || {
  printf 'Error: sha256sum is required.\n' >&2
  exit 1
}

if [[ ! -d "$BACKUP_DIR_INPUT" ]]; then
  printf 'Error: backup directory not found: %s\n' "$BACKUP_DIR_INPUT" >&2
  exit 1
fi
BACKUP_DIR="$(CDPATH= cd -- "$BACKUP_DIR_INPUT" && pwd)"
DATABASE_FILE="$BACKUP_DIR/database.dump"
COVERS_FILE="$BACKUP_DIR/covers.tar.gz"
CHECKSUM_FILE="$BACKUP_DIR/SHA256SUMS"

for required_file in "$DATABASE_FILE" "$COVERS_FILE" "$CHECKSUM_FILE"; do
  if [[ ! -f "$required_file" ]]; then
    printf 'Error: required backup file not found: %s\n' "$required_file" >&2
    exit 1
  fi
done

printf 'Verifying checksums...\n'
(
  cd -- "$BACKUP_DIR"
  sha256sum --check SHA256SUMS
)

cd -- "$PROJECT_DIR"
docker compose config --quiet
docker compose up -d --wait db

printf 'Verifying PostgreSQL archive...\n'
docker compose exec -T db sh -euc 'pg_restore --list >/dev/null' \
  < "$DATABASE_FILE"

printf 'Verifying covers archive...\n'
COVERS_LISTING="$(
  docker compose run --rm --no-deps -T --entrypoint sh backend -euc 'tar -tzf -' \
    < "$COVERS_FILE"
)"
while IFS= read -r archive_entry; do
  case "$archive_entry" in
    /*|../*|*/../*|*/..)
      printf 'Error: unsafe path in covers archive: %s\n' "$archive_entry" >&2
      exit 1
      ;;
  esac
done <<< "$COVERS_LISTING"

if [[ "$ASSUME_YES" != true ]]; then
  printf '\nWARNING: this replaces the current database and every cover.\n'
  printf 'Backup to restore: %s\n' "$BACKUP_DIR"
  printf 'Type RESTORE to continue: '
  IFS= read -r confirmation
  if [[ "$confirmation" != "RESTORE" ]]; then
    printf 'Restore cancelled. No data was changed.\n'
    exit 1
  fi
fi

if [[ "$SKIP_SAFETY_BACKUP" != true ]]; then
  printf 'Creating pre-restore safety backup...\n'
  "$SCRIPT_DIR/backup.sh" "$PROJECT_DIR/backups/pre-restore"
else
  printf 'Skipping the pre-restore safety backup by explicit request.\n'
fi

printf 'Stopping application services...\n'
docker compose stop backend frontend

printf 'Recreating PostgreSQL database...\n'
docker compose exec -T db sh -euc '
  dropdb --maintenance-db=postgres --if-exists --force --username="$POSTGRES_USER" "$POSTGRES_DB"
  createdb --maintenance-db=postgres --username="$POSTGRES_USER" --owner="$POSTGRES_USER" "$POSTGRES_DB"
'

docker compose exec -T db sh -euc \
  'pg_restore --exit-on-error --no-owner --no-privileges --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"' \
  < "$DATABASE_FILE"

printf 'Replacing covers...\n'
docker compose run --rm --no-deps -T --entrypoint sh backend -euc '
  find /app/data/covers -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
  tar -xzf - -C /app/data/covers
' < "$COVERS_FILE"

printf 'Starting application services...\n'
docker compose up -d --wait

printf 'Restore completed from: %s\n' "$BACKUP_DIR"
