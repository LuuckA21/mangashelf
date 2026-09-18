#!/usr/bin/env bash
# Installs/migrates the user service; no deploy or changes to application data.
set -Eeuo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/scripts/backup-common.sh"
case "${1:-}" in
  --cloud-from-kutt|--local) mode=$1 ;;
  *) echo 'Usage: ./install-backup.sh --cloud-from-kutt | --local' >&2; exit 2 ;;
esac
[[ $# == 1 ]]
[[ $(id -un) == appsvc ]] || { echo 'Run as appsvc.' >&2; exit 1; }
[[ "$PROJECT_DIR" =~ ^/[a-zA-Z0-9_./-]+$ && "$BACKUP_ROOT" =~ ^/[a-zA-Z0-9_./-]+$ ]]
export DBUS_SESSION_BUS_ADDRESS="unix:path=$XDG_RUNTIME_DIR/bus"
for cmd in docker flock sha256sum git install systemctl; do command -v "$cmd" >/dev/null; done
[[ -f "$PROJECT_DIR/.env" ]]
case "$(systemctl --user show mangashelf-backup.service -p ActiveState --value)" in
  inactive|failed|'') ;;
  *) echo 'Backup service is active; wait for it to finish.' >&2; exit 1 ;;
esac
unit_dir=${MANGASHELF_SYSTEMD_USER_DIR:-$HOME/.config/systemd/user}
dropin="$unit_dir/mangashelf-backup.service.d"
for file in "$dropin/"*.conf "$unit_dir/mangashelf-backup.timer.d/"*.conf; do
  [[ -e "$file" ]] || continue
  [[ "$file" == "$dropin/50-swiss-backup.conf" ]] || {
    echo "Unrecognized systemd override: $file. Review it before migration." >&2; exit 1;
  }
done

if [[ "$mode" == --cloud-from-kutt ]]; then
  for cmd in restic jq openssl; do command -v "$cmd" >/dev/null; done
  [[ ! -L "$CONFIG_DIR" ]]
  install -d -m 700 "$CONFIG_DIR"
  (
    source "${MANGASHELF_KUTT_CONFIG_DIR:-$HOME/.config/restic/kutt}/env.sh"
    [[ ${RESTIC_REPOSITORY:-} == s3:https://*/kutt ]]
    target="${RESTIC_REPOSITORY%/kutt}/mangashelf"
    [[ -n ${AWS_ACCESS_KEY_ID:-} && -n ${AWS_SECRET_ACCESS_KEY:-} ]]
    if [[ -e "$CONFIG_DIR/env.sh" ]]; then
      [[ ! -L "$CONFIG_DIR/env.sh" ]]
      source "$CONFIG_DIR/env.sh"
      [[ ${RESTIC_REPOSITORY:-} == "$target" ]] || {
        echo 'Existing MangaShelf repository differs; configuration left unchanged.' >&2; exit 1;
      }
      [[ -s ${RESTIC_PASSWORD_FILE:-} ]]
    else
      if [[ ! -e "$CONFIG_DIR/password" ]]; then
        (set -o noclobber; openssl rand -base64 48 > "$CONFIG_DIR/password")
      fi
      [[ -s "$CONFIG_DIR/password" && ! -L "$CONFIG_DIR/password" ]]
      tmp=$(mktemp "$CONFIG_DIR/.env-XXXXXXXX")
      trap 'rm -f -- "$tmp"' EXIT
      {
        printf 'export RESTIC_REPOSITORY=%q\n' "$target"
        printf 'export RESTIC_PASSWORD_FILE=%q\n' "$CONFIG_DIR/password"
        printf 'export AWS_ACCESS_KEY_ID=%q\n' "$AWS_ACCESS_KEY_ID"
        printf 'export AWS_SECRET_ACCESS_KEY=%q\n' "$AWS_SECRET_ACCESS_KEY"
        printf 'export AWS_DEFAULT_REGION=%q\n' "${AWS_DEFAULT_REGION:-us-east-1}"
      } > "$tmp"
      mv -- "$tmp" "$CONFIG_DIR/env.sh"
    fi
    if [[ -e "$CONFIG_DIR/repository" ]]; then
      [[ ! -L "$CONFIG_DIR/repository" && $(cat "$CONFIG_DIR/repository") == "$target" ]]
    else
      (set -o noclobber; printf '%s\n' "$target" > "$CONFIG_DIR/repository")
    fi
  )
  load_restic_config
  if ! restic cat config >/dev/null; then
    # init refuses an already initialized repository: do not replace any key/config.
    restic init
  fi
fi

# Save old units and marker, including the earlier standalone kit's drop-in.
install -d -m 700 "$unit_dir"
previous=$(mktemp -d "$unit_dir/mangashelf-backup-previous-XXXXXXXX")
for name in mangashelf-backup.service mangashelf-backup.timer mangashelf-backup.service.d; do
  if [[ -e "$unit_dir/$name" ]]; then cp -a -- "$unit_dir/$name" "$previous/"; fi
done
if [[ -f "$BACKUP_ROOT/last-success" ]]; then cp -p -- "$BACKUP_ROOT/last-success" "$previous/last-success"; fi
echo "Previous configuration preserved: $previous"
for name in mangashelf-backup.service mangashelf-backup.timer; do
  sed -e "s|/srv/apps/mangashelf|$PROJECT_DIR|g" -e "s|/srv/backups/mangashelf|$BACKUP_ROOT|g" \
    "$PROJECT_DIR/ops/systemd/$name" > "$unit_dir/$name"
done
if [[ "$mode" == --cloud-from-kutt ]]; then
  install -d -m 700 "$dropin"
  sed "s|/srv/apps/mangashelf|$PROJECT_DIR|g" "$PROJECT_DIR/ops/systemd/50-swiss-backup.conf" > "$dropin/50-swiss-backup.conf"
else
  rm -f -- "$dropin/50-swiss-backup.conf"
fi
systemctl --user daemon-reload
if ! systemctl --user start mangashelf-backup.service; then
  journalctl --user -u mangashelf-backup.service -n 80 --no-pager
  echo "Installation test failed. Previous units are in $previous." >&2
  exit 1
fi
systemctl --user enable mangashelf-backup.timer
systemctl --user restart mangashelf-backup.timer
systemctl --user show mangashelf-backup.service -p Result -p ExecMainStatus
systemctl --user list-timers mangashelf-backup.timer --no-pager
cat "$BACKUP_ROOT/last-success"
if [[ "$mode" == --cloud-from-kutt ]]; then cat "$BACKUP_ROOT/last-cloud-success.json"; fi
echo 'Backup installation and first run completed.'
