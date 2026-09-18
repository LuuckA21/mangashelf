#!/usr/bin/env bash
# Shared configuration for the Kutt-style operational entry points.
set -Eeuo pipefail
umask 077
PROJECT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
default_root="$PROJECT_DIR/backups"
if [[ "$PROJECT_DIR" == /srv/apps/mangashelf ]]; then default_root=/srv/backups/mangashelf; fi
BACKUP_ROOT=${MANGASHELF_BACKUP_ROOT:-$default_root}
mkdir -p -- "$BACKUP_ROOT"
BACKUP_ROOT=$(cd -- "$BACKUP_ROOT" && pwd -P)
export TZ=Europe/Zurich
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
if [[ -z ${DOCKER_HOST:-} && -S "$XDG_RUNTIME_DIR/docker.sock" ]]; then
  export DOCKER_HOST="unix://$XDG_RUNTIME_DIR/docker.sock"
fi
CONFIG_DIR=${MANGASHELF_RESTIC_CONFIG_DIR:-$HOME/.config/restic/mangashelf}

load_restic_config() {
  [[ -f "$CONFIG_DIR/env.sh" && ! -L "$CONFIG_DIR/env.sh" ]]
  source "$CONFIG_DIR/env.sh"
  [[ -s "$CONFIG_DIR/repository" && ! -L "$CONFIG_DIR/repository" ]]
  [[ -n ${RESTIC_REPOSITORY:-} && "$RESTIC_REPOSITORY" == "$(cat "$CONFIG_DIR/repository")" ]] || {
    echo 'Unexpected Restic repository. Check env.sh and repository.' >&2; exit 1;
  }
  [[ "$RESTIC_REPOSITORY" == */mangashelf ]] || {
    echo 'Use a dedicated repository ending in /mangashelf.' >&2; exit 1;
  }
  [[ -s ${RESTIC_PASSWORD_FILE:-} && -r ${RESTIC_PASSWORD_FILE:-} ]]
  [[ -n ${AWS_ACCESS_KEY_ID:-} && -n ${AWS_SECRET_ACCESS_KEY:-} ]]
  export RESTIC_REPOSITORY RESTIC_PASSWORD_FILE AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY
  export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-us-east-1}"
}

latest_managed_backup() {
  local path name
  path=$(cat "$BACKUP_ROOT/last-success")
  name=${path##*/}
  [[ "${path%/*}" == "$BACKUP_ROOT" && "$name" =~ ^daily-[0-9]{8}-[0-9]{6}-[0-9]{9}$ ]]
  [[ -d "$path" && ! -L "$path" ]]
  [[ -f "$path/.managed-mangashelf-backup-v1" && ! -L "$path/.managed-mangashelf-backup-v1" ]]
  [[ $(cat "$path/.managed-mangashelf-backup-v1") == mangashelf-backup-v1 ]]
  printf '%s\n' "$path"
}
