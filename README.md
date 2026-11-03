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

# Terminal 1: backend (starts the DB container too)
cd backend && ./mvnw spring-boot:run

# Terminal 2: frontend
cd frontend && npm ci && npm run dev       # -> http://localhost:5173
```

## Scripts

| Command | What it does |
|---------|--------------|
| `cd backend && ./mvnw verify` | Backend tests + format check |
| `cd backend && ./mvnw spotless:apply` | Format Java |
| `cd frontend && npm run dev` | Run frontend |

## Environments

| Env | URL | Deploys from |
|-----|-----|--------------|
| staging | | `main` |
| prod | | release / `main` |

## Deploy & rollback

How to deploy:

How to roll back:

## Backup & restore

- RPO: 24h · RTO: <fill in>
- Backups: what, where, schedule
- Restore runbook: `docs/runbooks/restore.md`
- Last restore test: YYYY-MM-DD

## Standards

This project follows [project-standards](https://github.com/LuuckA21/project-standards).
