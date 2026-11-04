# mangashelf

App web self-hosted per me e pochi amici: ognuno tiene traccia della propria collezione manga, volume per volume, su un catalogo di serie ed edizioni condiviso.

## Tech stack

- Runtime / framework: Java 25, Spring Boot 4 (backend); React + TypeScript + Vite (frontend)
- Database: PostgreSQL 17 (Flyway)
- Hosting: self-hosted, Docker

## Getting started

```bash
# Tools (once per machine): Docker, SDKMAN (Java), fnm or nvm (Node)
sdk env install                # installs the Java version in .sdkmanrc
fnm install                    # reads .nvmrc

cp backend/.env.example backend/.env       # dev values, never real secrets

# Terminal 1: backend, dev profile (starts the DB container too and loads the dev seed data)
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Terminal 2: frontend
cd frontend && npm ci && npm run dev       # -> http://localhost:5173
```

Dev users (profile `dev` only, from `backend/src/main/resources/db/dev-data/`): `admin` / `admin-dev-password`
(admin) and `amico` / `amico-dev-password`. The admin's collection has One Piece "Edizione blu" with volumes 1-110
of 114 and "Volume 1 - Oro" with none. Open the app at http://localhost:5173 (Vite proxies `/api` to `:8080`).

## Scripts

| Command | What it does |
|---------|--------------|
| `cd backend && ./mvnw verify` | Backend tests + format check |
| `cd backend && ./mvnw spotless:apply` | Format Java |
| `cd frontend && npm run dev` | Run frontend |
| `cd frontend && npm run lint && npm run typecheck && npm test` | Frontend checks |

## Environments

| Env | URL | Deploys from |
|-----|-----|--------------|
| staging | | `main` |
| prod | | release / `main` |

## Deploy & rollback

One Docker image (`ghcr.io/luucka21/mangashelf`) serves the API and the React app. Server `prd-apps-01`,
folder `/srv/apps/mangashelf` (production, port **8082**) and `/srv/apps/mangashelf-staging` (staging, port
**8083**). Details: [05 - deploy & environments](https://github.com/LuuckA21/project-standards/blob/main/05-deploy-environments.md).

How to deploy: merge to `main` deploys staging; publish a release (version bump + `CHANGELOG.md` in the PR) to
deploy production. Both run `deploy.yml`.

Server `.env` (never committed; values in Bitwarden), besides the database and `APP_URL` / `FRONTEND_ORIGIN`:

- `ADMIN_USERNAME` and `ADMIN_INITIAL_PASSWORD`: create the first admin, used **only for the first start**
  (when no user exists); the admin must change the password at first login. Remove them afterwards.
- `BACKUP_UPLOADS_DIR=/app/uploads`: cover images, included in the backup.

Nginx Proxy Manager: rate-limit login. Zone `app_auth` is in `http_top.conf` of the standards; in the proxy
host's *Advanced* tab:

```nginx
location /api/v1/auth/login {
    limit_req zone=app_auth burst=10 nodelay;
    limit_req_status 429;
    include conf.d/include/proxy.conf;
}
```

Monitoring: uptime check on `/actuator/health`.

How to roll back: redeploy the previous release (the deploy rolls back automatically if the health check fails);
migrations are backwards-compatible, so code rolls back without touching data.

## Backup & restore

- RPO: 24h · RTO: <fill in>
- Backups: PostgreSQL and covers (`/app/uploads`) with `backup.sh`, restic / Swiss Backup, daily
- Restore runbook: `docs/runbooks/restore.md`
- Last restore test: YYYY-MM-DD

## Standards

This project follows [project-standards](https://github.com/LuuckA21/project-standards).
