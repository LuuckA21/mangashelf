# Backup & restore runbook — MangaShelf

Follows [project-standards 01-backup](https://github.com/LuuckA21/project-standards/blob/main/01-backup.md);
this page only fills in MangaShelf's names. `<app>` is `mangashelf` (production) or `mangashelf-staging`.

Where: **PC** = your terminal · **opsadmin** = `ssh` to the app server · **appsvc** = from opsadmin
`sudo machinectl shell appsvc@`.

## Set up backups (once per environment)

1. Where: **appsvc**. Repository password, then copy it into Bitwarden ("MangaShelf <env> – restic password"):

   ```bash
   (umask 077; openssl rand -base64 32 > /etc/backup/<app>.restic-password)
   cat /etc/backup/<app>.restic-password
   ```

2. Where: **appsvc**. `nano /etc/backup/<app>.env`, then `chmod 600 /etc/backup/<app>.env`:

   ```
   RESTIC_REPOSITORY=s3:https://s3.swiss-backupNN.infomaniak.com/<bucket>/<app>
   RESTIC_PASSWORD_FILE=/etc/backup/<app>.restic-password
   AWS_ACCESS_KEY_ID=<Swiss Backup access key>
   AWS_SECRET_ACCESS_KEY=<Swiss Backup secret key>
   BACKUP_DB_TYPE=postgres
   BACKUP_APP_DIR=/srv/apps/<app>
   BACKUP_UPLOADS_DIR=/app/uploads
   BACKUP_HEALTHCHECK_URL=
   ```

3. Where: **appsvc**. `set -a; . /etc/backup/<app>.env; set +a; restic init`
4. Where: **PC** (in project-standards) `scp templates/server/backup.sh <server>:`, then **opsadmin**
   `sudo install -m 0755 -o appsvc -g appsvc ~/backup.sh /srv/apps/<app>/backup.sh && rm ~/backup.sh`
5. Where: **appsvc**. First backup by hand, last line `Backup OK`; `restic snapshots` shows `/postgres.sql`
   (tag `db`) and `/uploads.tar` (tag `files`):

   ```bash
   /srv/apps/<app>/backup.sh /etc/backup/<app>.env
   ```

6. Production only. Where: **opsadmin**. `sudo nano /etc/cron.d/mangashelf-backup`:

   ```cron
   17 3 * * *  appsvc  /srv/apps/mangashelf/backup.sh /etc/backup/mangashelf.env 2>&1 | systemd-cat -t mangashelf-backup
   43 4 * * 0  appsvc  bash -c 'set -a; . /etc/backup/mangashelf.env; restic check --read-data-subset=5\%' 2>&1 | systemd-cat -t mangashelf-backup
   ```

   Another app on the same server already at 03:17? Shift by 10 minutes. Next morning:
   `journalctl -t mangashelf-backup --since yesterday` ends with `Backup OK`.

## Restore

Where: **appsvc**. Start every session with:

```bash
set -a; . /etc/backup/<app>.env; set +a
cd /srv/apps/<app>
restic snapshots          # pick one, or use "latest"
```

### 1. Check the snapshot in a scratch database (touches nothing)

```bash
docker compose exec -T db sh -c 'createdb -U "$POSTGRES_USER" app_restore'
restic dump latest /postgres.sql --tag db \
  | docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -v ON_ERROR_STOP=1 -q app_restore'
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" app_restore -c "SELECT (SELECT count(*) FROM users) users, (SELECT count(*) FROM series) series, (SELECT count(*) FROM editions) editions, (SELECT count(*) FROM owned_volumes) volumes;"'
# Same query on "$POSTGRES_DB" instead of app_restore: the numbers should be plausible next to the live ones.
restic dump latest /uploads.tar --tag files | tar -tf - | grep -vc '/$'   # cover files in the snapshot
docker compose exec -T app find /app/uploads -type f | wc -l            # cover files now
docker compose exec -T db sh -c 'dropdb -U "$POSTGRES_USER" app_restore'
```

### 2. Restore for real (the app is down meanwhile)

```bash
docker compose stop app
docker compose exec -T db sh -c 'dropdb -U "$POSTGRES_USER" "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
restic dump latest /postgres.sql --tag db \
  | docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -v ON_ERROR_STOP=1 -q "$POSTGRES_DB"'
docker compose start app
# Covers: through the running app container (rootless Docker: the host user can't read the volume).
restic dump latest /uploads.tar --tag files | docker compose exec -T app tar -xf - -C /app/uploads
```

Open the site: users may have to log in again. Check a series you know, with its cover.

## Restore tests

| Date | Environment | What | Result | Time (RTO) |
|------|-------------|------|--------|------------|
| 2026-11-05 | staging | Full restore (section 2) after deleting a test series with a cover | Restore OK | not measured |
