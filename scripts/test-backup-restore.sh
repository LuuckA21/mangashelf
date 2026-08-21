#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEST_ROOT="$(mktemp -d)"
cleanup() {
  rm -rf -- "$TEST_ROOT"
}
trap cleanup EXIT

mkdir -p -- "$TEST_ROOT/bin"
ln -s -- "$SCRIPT_DIR/tests/fake-docker" "$TEST_ROOT/bin/docker"
export PATH="$TEST_ROOT/bin:$PATH"
export FAKE_DOCKER_LOG="$TEST_ROOT/docker.log"

BACKUP_ROOT="$TEST_ROOT/backups"
(
  cd -- "$TEST_ROOT"
  "$SCRIPT_DIR/backup.sh" backups >/dev/null
)

BACKUP_DIR="$(find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -name 'mangashelf-*' -print -quit)"
if [[ -z "$BACKUP_DIR" ]]; then
  printf 'Test failure: backup directory was not created.\n' >&2
  exit 1
fi

for expected_file in database.dump covers.tar.gz manifest.txt SHA256SUMS; do
  if [[ ! -s "$BACKUP_DIR/$expected_file" ]]; then
    printf 'Test failure: missing or empty backup file: %s\n' "$expected_file" >&2
    exit 1
  fi
done

(
  cd -- "$BACKUP_DIR"
  sha256sum --check SHA256SUMS >/dev/null
)

"$SCRIPT_DIR/restore.sh" "$BACKUP_DIR" --yes --skip-safety-backup >/dev/null

for expected_command in \
  'compose stop backend frontend' \
  'dropdb --maintenance-db=postgres' \
  'pg_restore --exit-on-error' \
  'tar -xzf - -C /app/data/covers'; do
  if ! grep -Fq -- "$expected_command" "$FAKE_DOCKER_LOG"; then
    printf 'Test failure: restore did not execute: %s\n' "$expected_command" >&2
    exit 1
  fi
done

cp -R -- "$BACKUP_DIR" "$TEST_ROOT/tampered-backup"
printf 'tampered' >> "$TEST_ROOT/tampered-backup/database.dump"
if "$SCRIPT_DIR/restore.sh" "$TEST_ROOT/tampered-backup" --yes --skip-safety-backup \
    >/dev/null 2>&1; then
  printf 'Test failure: restore accepted a corrupted backup.\n' >&2
  exit 1
fi

printf 'Backup and restore script tests passed.\n'
