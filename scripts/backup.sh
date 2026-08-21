#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname -- "$SCRIPT_DIR")"

usage() {
  printf 'Usage: %s [backup-directory]\n' "${0##*/}"
  printf 'Creates a verified PostgreSQL and covers backup.\n'
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

if (( $# > 1 )); then
  usage >&2
  exit 2
fi

command -v docker >/dev/null 2>&1 || {
  printf 'Error: docker is required.\n' >&2
  exit 1
}
command -v sha256sum >/dev/null 2>&1 || {
  printf 'Error: sha256sum is required.\n' >&2
  exit 1
}

BACKUP_ROOT_INPUT="${1:-$PROJECT_DIR/backups}"
mkdir -p -- "$BACKUP_ROOT_INPUT"
BACKUP_ROOT="$(CDPATH= cd -- "$BACKUP_ROOT_INPUT" && pwd)"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
FINAL_DIR="$BACKUP_ROOT/mangashelf-$TIMESTAMP"

if [[ -e "$FINAL_DIR" ]]; then
  printf 'Error: backup destination already exists: %s\n' "$FINAL_DIR" >&2
  exit 1
fi

TMP_DIR="$(mktemp -d "$BACKUP_ROOT/.mangashelf-$TIMESTAMP.XXXXXX")"
cleanup() {
  if [[ -n "${TMP_DIR:-}" && -d "$TMP_DIR" ]]; then
    rm -rf -- "$TMP_DIR"
  fi
}
trap cleanup EXIT

cd -- "$PROJECT_DIR"
docker compose config --quiet
docker compose up -d --wait db

printf 'Creating PostgreSQL backup...\n'
docker compose exec -T db sh -euc \
  'pg_dump --format=custom --compress=9 --no-owner --no-privileges --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"' \
  > "$TMP_DIR/database.dump"

if [[ ! -s "$TMP_DIR/database.dump" ]]; then
  printf 'Error: PostgreSQL produced an empty backup.\n' >&2
  exit 1
fi

docker compose exec -T db sh -euc 'pg_restore --list >/dev/null' \
  < "$TMP_DIR/database.dump"

printf 'Creating covers backup...\n'
docker compose run --rm --no-deps -T --entrypoint sh backend -euc \
  'tar -C /app/data/covers -czf - .' \
  > "$TMP_DIR/covers.tar.gz"

docker compose run --rm --no-deps -T --entrypoint sh backend -euc \
  'tar -tzf - >/dev/null' \
  < "$TMP_DIR/covers.tar.gz"

GIT_COMMIT="$(git rev-parse --verify HEAD 2>/dev/null || printf 'unknown')"
{
  printf 'created_at_utc=%s\n' "$TIMESTAMP"
  printf 'git_commit=%s\n' "$GIT_COMMIT"
  printf 'database=database.dump\n'
  printf 'covers=covers.tar.gz\n'
} > "$TMP_DIR/manifest.txt"

(
  cd -- "$TMP_DIR"
  sha256sum database.dump covers.tar.gz manifest.txt > SHA256SUMS
  sha256sum --check SHA256SUMS >/dev/null
)

mv -- "$TMP_DIR" "$FINAL_DIR"
TMP_DIR=''
trap - EXIT

printf 'Backup completed: %s\n' "$FINAL_DIR"
