#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEST_ROOT="$(mktemp -d)"
cleanup() {
  rm -rf -- "$TEST_ROOT"
}
trap cleanup EXIT

fail() {
  printf 'Test failure: %s\n' "$1" >&2
  exit 1
}

mkdir -p -- "$TEST_ROOT/bin"
ln -s -- "$SCRIPT_DIR/tests/fake-docker" "$TEST_ROOT/bin/docker"

cat > "$TEST_ROOT/bin/date" <<'FAKE_DATE'
#!/usr/bin/env bash
set -euo pipefail
case "$*" in
  '-u +%Y%m%dT%H%M%SZ') printf '%s\n' "$FAKE_BACKUP_TIMESTAMP" ;;
  '+%u') printf '%s\n' "$FAKE_BACKUP_WEEKDAY" ;;
  '-Is') printf '%s\n' "${FAKE_BACKUP_NOW:-2026-08-21T03:30:00+02:00}" ;;
  *) printf 'Unexpected fake date arguments: %s\n' "$*" >&2; exit 2 ;;
esac
FAKE_DATE
chmod +x -- "$TEST_ROOT/bin/date"

export PATH="$TEST_ROOT/bin:$PATH"
export FAKE_DOCKER_LOG="$TEST_ROOT/docker.log"
export MANGASHELF_BACKUP_ROOT="$TEST_ROOT/backups"

for day in 01 02 03 04 05 06 07 08 09; do
  export FAKE_BACKUP_TIMESTAMP="202608${day}T013000Z"
  if (( 10#$day <= 5 )); then
    export FAKE_BACKUP_WEEKDAY=7
  else
    export FAKE_BACKUP_WEEKDAY=1
  fi
  "$SCRIPT_DIR/backup-scheduled.sh" >/dev/null
done

DAILY_COUNT="$(find "$MANGASHELF_BACKUP_ROOT/daily" -mindepth 1 -maxdepth 1 \
  -type d -name 'mangashelf-*' | wc -l)"
WEEKLY_COUNT="$(find "$MANGASHELF_BACKUP_ROOT/weekly" -mindepth 1 -maxdepth 1 \
  -type d -name 'mangashelf-*' | wc -l)"

[[ "$DAILY_COUNT" == 7 ]] || fail "expected 7 daily backups, found $DAILY_COUNT"
[[ "$WEEKLY_COUNT" == 4 ]] || fail "expected 4 weekly backups, found $WEEKLY_COUNT"
[[ ! -e "$MANGASHELF_BACKUP_ROOT/daily/mangashelf-20260801T013000Z" ]] ||
  fail 'oldest daily backup was not pruned'
[[ -d "$MANGASHELF_BACKUP_ROOT/daily/mangashelf-20260803T013000Z" ]] ||
  fail 'newest seven daily backups were not retained'
[[ ! -e "$MANGASHELF_BACKUP_ROOT/weekly/mangashelf-20260801T013000Z" ]] ||
  fail 'oldest weekly backup was not pruned'
[[ -d "$MANGASHELF_BACKUP_ROOT/weekly/mangashelf-20260802T013000Z" ]] ||
  fail 'newest four weekly backups were not retained'

DAILY_INODE="$(stat -c '%i' \
  "$MANGASHELF_BACKUP_ROOT/daily/mangashelf-20260805T013000Z/database.dump")"
WEEKLY_INODE="$(stat -c '%i' \
  "$MANGASHELF_BACKUP_ROOT/weekly/mangashelf-20260805T013000Z/database.dump")"
[[ "$DAILY_INODE" == "$WEEKLY_INODE" ]] ||
  fail 'weekly snapshot does not use hard links'

(
  cd -- "$MANGASHELF_BACKUP_ROOT/weekly/mangashelf-20260802T013000Z"
  sha256sum --check SHA256SUMS >/dev/null
)

grep -Fq 'daily_retention=7' "$MANGASHELF_BACKUP_ROOT/last-success" ||
  fail 'last-success does not record daily retention'
grep -Fq 'weekly_retention=4' "$MANGASHELF_BACKUP_ROOT/last-success" ||
  fail 'last-success does not record weekly retention'

export FAKE_BACKUP_TIMESTAMP=20260810T013000Z
export FAKE_BACKUP_WEEKDAY=1
export FAKE_DOCKER_FAIL=1
if "$SCRIPT_DIR/backup-scheduled.sh" >/dev/null 2>&1; then
  fail 'scheduled backup accepted a Docker failure'
fi
unset FAKE_DOCKER_FAIL

grep -Fq 'exit_code=42' "$MANGASHELF_BACKUP_ROOT/last-failure" ||
  fail 'failed backup did not record its exit code'
[[ -f "$MANGASHELF_BACKUP_ROOT/last-success" ]] ||
  fail 'a failure removed the last successful backup state'

printf 'Scheduled backup tests passed.\n'
