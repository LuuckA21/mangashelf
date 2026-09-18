#!/usr/bin/env bash
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
fixture=$(mktemp -d)
trap 'rm -rf -- "$fixture"' EXIT
# Unit validation only: the CI runner need not have a rootless user daemon.
printf '[Service]\nExecStart=/usr/bin/true\n' > "$fixture/docker.service"
SYSTEMD_UNIT_PATH="$fixture:" systemd-analyze verify \
  "$root/ops/systemd/mangashelf-backup.service" "$root/ops/systemd/mangashelf-backup.timer"
mkdir -p "$fixture/mangashelf-backup.service.d"
cp "$root/ops/systemd/mangashelf-backup.service" "$fixture/"
cp "$root/ops/systemd/50-swiss-backup.conf" "$fixture/mangashelf-backup.service.d/"
SYSTEMD_UNIT_PATH="$fixture:" systemd-analyze verify "$fixture/mangashelf-backup.service"
