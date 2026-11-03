# CLAUDE.md

This project follows the shared project standards. Read and apply them.
The standards repository is private: if you can't open these links, ask the user to attach
the repository `LuuckA21/project-standards` to this session (or to give you a local clone) before going on.

- Backup: https://github.com/LuuckA21/project-standards/blob/main/01-backup.md
- Git workflow: https://github.com/LuuckA21/project-standards/blob/main/02-git-workflow.md
- Security & secrets: https://github.com/LuuckA21/project-standards/blob/main/03-security-secrets.md
- Structure & code quality: https://github.com/LuuckA21/project-standards/blob/main/04-structure-quality.md
- Deploy & environments: https://github.com/LuuckA21/project-standards/blob/main/05-deploy-environments.md
- Server hardening & maintenance: https://github.com/LuuckA21/project-standards/blob/main/06-server-maintenance.md
- API design: https://github.com/LuuckA21/project-standards/blob/main/07-api-design.md
- Database: https://github.com/LuuckA21/project-standards/blob/main/08-database.md
- Privacy & legal: https://github.com/LuuckA21/project-standards/blob/main/09-privacy-legal.md
- Local development: https://github.com/LuuckA21/project-standards/blob/main/10-local-development.md
- Frontend & accessibility: https://github.com/LuuckA21/project-standards/blob/main/11-frontend.md
- Working with Claude Code: https://github.com/LuuckA21/project-standards/blob/main/12-claude-code.md
- New server from scratch: https://github.com/LuuckA21/project-standards/blob/main/13-new-server.md
- SSH keys & password hashes: https://github.com/LuuckA21/project-standards/blob/main/14-keys-and-passwords.md

## How you work here (12-claude-code.md)

- Work on a branch; commit and push the branch. Open a PR only when asked.
- Never merge, bump the version, publish a release, deploy or change GitHub settings unless the user asks
  in this conversation, for this change.
- Never read or print `.env` files, keys or other secrets; never SSH into servers.
- Run the tests and show the result before saying something works; say what you did not test.

## Key rules (summary)

- Never commit secrets. New config → add it to `.env.example` with a comment.
- Conventional Commits; small PRs; `main` always deployable.
- Validate input at trust boundaries; parameterized queries only.
- Every bug fix gets a test. Formatter, linter, type checker and tests must pass.
- Database migrations must be backwards-compatible.
- APIs under `/api/v1`, errors as Problem Details, DTO records only (07-api-design.md).
- DB: plural snake_case tables, UUID v7 ids, UTC timestamps, indexed FKs, physical deletes (08-database.md).
- New personal data field, purpose or processor → update `docs/privacy.md` in the same PR (09-privacy-legal.md).
- Follow the Definition of Done in 04-structure-quality.md.

## Project specifics

- Backend (in `backend/`): run `./mvnw spring-boot:run` · test `./mvnw verify` · format `./mvnw spotless:apply`
- Frontend (in `frontend/`): run `npm run dev` · check `npm run lint && npm run typecheck && npm test`
- Releasing: in the PR, bump `<version>` in `backend/pom.xml` (`./mvnw versions:set -DnewVersion=X.Y.Z -DgenerateBackupPoms=false`) and add the `## [X.Y.Z] - YYYY-MM-DD` section to `CHANGELOG.md`
- DB changes: new Flyway migration in `backend/src/main/resources/db/migration/`, never edit an applied one
