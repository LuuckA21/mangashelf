#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname -- "$SCRIPT_DIR")"

BACKUP_ROOT_INPUT="${MANGASHELF_BACKUP_ROOT:-$PROJECT_DIR/backups}"
DAILY_RETENTION="${MANGASHELF_DAILY_RETENTION:-7}"
WEEKLY_RETENTION="${MANGASHELF_WEEKLY_RETENTION:-4}"
WEEKLY_DAY="${MANGASHELF_WEEKLY_DAY:-7}"

require_positive_integer() {
  local name=$1
  local value=$2
  if [[ ! "$value" =~ ^[1-9][0-9]*$ ]]; then
    printf 'Error: %s must be a positive integer.\n' "$name" >&2
    exit 2
  fi
}

require_positive_integer MANGASHELF_DAILY_RETENTION "$DAILY_RETENTION"
require_positive_integer MANGASHELF_WEEKLY_RETENTION "$WEEKLY_RETENTION"
if [[ ! "$WEEKLY_DAY" =~ ^[1-7]$ ]]; then
  printf 'Error: MANGASHELF_WEEKLY_DAY must be between 1 and 7.\n' >&2
  exit 2
fi

mkdir -p -- "$BACKUP_ROOT_INPUT"
BACKUP_ROOT="$(CDPATH= cd -- "$BACKUP_ROOT_INPUT" && pwd)"
DAILY_ROOT="$BACKUP_ROOT/daily"
WEEKLY_ROOT="$BACKUP_ROOT/weekly"
SUCCESS_FILE="$BACKUP_ROOT/last-success"
FAILURE_FILE="$BACKUP_ROOT/last-failure"
mkdir -p -- "$DAILY_ROOT" "$WEEKLY_ROOT"

TEMP_LOG="$(mktemp "$BACKUP_ROOT/.scheduled-backup.XXXXXX")"
WEEKLY_TEMP=""
cleanup() {
  if [[ -n "${TEMP_LOG:-}" && -f "$TEMP_LOG" ]]; then
    rm -f -- "$TEMP_LOG"
  fi
  if [[ -n "${WEEKLY_TEMP:-}" && -e "$WEEKLY_TEMP" ]]; then
    rm -rf -- "$WEEKLY_TEMP"
  fi
}
trap cleanup EXIT

record_failure() {
  local exit_code=$1
  local failed_at failure_tmp

  trap - ERR
  failed_at="$(date -Is 2>/dev/null || printf 'unknown')"
  failure_tmp="$(mktemp "$BACKUP_ROOT/.last-failure.XXXXXX")"
  {
    printf 'failed_at=%s\n' "$failed_at"
    printf 'exit_code=%s\n' "$exit_code"
  } > "$failure_tmp"
  mv -- "$failure_tmp" "$FAILURE_FILE"
  printf 'Scheduled backup failed with exit code %s.\n' "$exit_code" >&2
  exit "$exit_code"
}
trap 'record_failure $?' ERR

prune_backups() {
  local backup_root=$1
  local keep=$2
  local label=$3
  local index backup_name backup_path
  local -a backup_names=()

  mapfile -t backup_names < <(
    find "$backup_root" -mindepth 1 -maxdepth 1 -type d \
      -name 'mangashelf-*Z' -printf '%f\n' | LC_ALL=C sort -r
  )

  for (( index = keep; index < ${#backup_names[@]}; index++ )); do
    backup_name="${backup_names[$index]}"
    if [[ ! "$backup_name" =~ ^mangashelf-[0-9]{8}T[0-9]{6}Z$ ]]; then
      printf 'Refusing to prune unexpected %s path: %s\n' \
        "$label" "$backup_name" >&2
      return 1
    fi
    backup_path="$backup_root/$backup_name"
    printf 'Removing expired %s backup: %s\n' "$label" "$backup_path"
    rm -rf -- "$backup_path"
  done
}

printf 'Starting scheduled MangaShelf backup...\n'
"$SCRIPT_DIR/backup.sh" "$DAILY_ROOT" | tee "$TEMP_LOG"

BACKUP_DIR="$(sed -n 's/^Backup completed: //p' "$TEMP_LOG" | tail -n 1)"
if [[ -z "$BACKUP_DIR" || ! -d "$BACKUP_DIR" ]]; then
  printf 'Error: backup destination was not reported or does not exist.\n' >&2
  false
fi

WEEKLY_BACKUP=""
if [[ "$(date +%u)" == "$WEEKLY_DAY" ]]; then
  WEEKLY_BACKUP="$WEEKLY_ROOT/$(basename -- "$BACKUP_DIR")"
  if [[ -e "$WEEKLY_BACKUP" ]]; then
    printf 'Error: weekly backup already exists: %s\n' "$WEEKLY_BACKUP" >&2
    false
  fi
  WEEKLY_TEMP="$(mktemp -d "$WEEKLY_ROOT/.weekly.XXXXXX")"
  rmdir -- "$WEEKLY_TEMP"
  cp -al -- "$BACKUP_DIR" "$WEEKLY_TEMP"
  mv -- "$WEEKLY_TEMP" "$WEEKLY_BACKUP"
  WEEKLY_TEMP=""
  printf 'Weekly snapshot created: %s\n' "$WEEKLY_BACKUP"
fi

prune_backups "$DAILY_ROOT" "$DAILY_RETENTION" daily
prune_backups "$WEEKLY_ROOT" "$WEEKLY_RETENTION" weekly

SUCCESS_TMP="$(mktemp "$BACKUP_ROOT/.last-success.XXXXXX")"
{
  printf 'completed_at=%s\n' "$(date -Is)"
  printf 'daily_backup=%s\n' "$BACKUP_DIR"
  printf 'weekly_backup=%s\n' "${WEEKLY_BACKUP:-none}"
  printf 'daily_retention=%s\n' "$DAILY_RETENTION"
  printf 'weekly_retention=%s\n' "$WEEKLY_RETENTION"
} > "$SUCCESS_TMP"
mv -- "$SUCCESS_TMP" "$SUCCESS_FILE"
rm -f -- "$FAILURE_FILE"

printf 'Scheduled backup completed successfully.\n'
