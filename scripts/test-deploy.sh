#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname -- "$SCRIPT_DIR")"
DEPLOY_SCRIPT="$PROJECT_DIR/deploy.sh"
TEST_ROOT="$(mktemp -d)"
cleanup() {
    rm -rf -- "$TEST_ROOT"
}
trap cleanup EXIT

fail() {
    printf 'Test failure: %s\n' "$1" >&2
    exit 1
}

assert_file_contains() {
    local file=$1
    local expected=$2
    grep -Fq -- "$expected" "$file" || fail "$file does not contain: $expected"
}

mkdir -p -- "$TEST_ROOT/bin" "$TEST_ROOT/host" "$TEST_ROOT/seed"

cat > "$TEST_ROOT/bin/docker" <<'FAKE_DOCKER'
#!/usr/bin/env bash
set -euo pipefail
printf 'docker %s\n' "$*" >> "$FAKE_DEPLOY_LOG"

case "$*" in
    info|"compose version"|"image prune -f"|"compose ps"|"compose logs"*)
        exit 0
        ;;
    "compose up "*)
        if [[ "${FAKE_DOCKER_FAIL_UP:-0}" == 1 ]]; then
            exit 42
        fi
        ;;
    "compose exec -T backend "*)
        printf '{"status":"UP"}\n'
        ;;
    "compose exec -T frontend "*)
        printf '<html>ok</html>\n'
        ;;
esac
FAKE_DOCKER

cat > "$TEST_ROOT/bin/loginctl" <<'FAKE_LOGINCTL'
#!/usr/bin/env bash
printf 'yes\n'
FAKE_LOGINCTL
chmod +x -- "$TEST_ROOT/bin/docker" "$TEST_ROOT/bin/loginctl"

git init --bare --initial-branch=master "$TEST_ROOT/origin.git" >/dev/null
git -C "$TEST_ROOT/seed" init --initial-branch=master >/dev/null
git -C "$TEST_ROOT/seed" config user.name 'Deploy test'
git -C "$TEST_ROOT/seed" config user.email 'deploy-test@example.invalid'

mkdir -p -- "$TEST_ROOT/seed/scripts"
cat > "$TEST_ROOT/seed/scripts/backup.sh" <<'FAKE_BACKUP'
#!/usr/bin/env bash
set -euo pipefail
printf 'backup %s\n' "$(git rev-parse HEAD)" >> "$FAKE_DEPLOY_LOG"
printf 'Backup completed: %s/backups/fake\n' "$PWD"
FAKE_BACKUP
chmod +x -- "$TEST_ROOT/seed/scripts/backup.sh"
printf 'POSTGRES_PASSWORD=change-me\n' > "$TEST_ROOT/seed/.env.example"
printf 'services: {}\n' > "$TEST_ROOT/seed/docker-compose.yml"
printf 'initial\n' > "$TEST_ROOT/seed/app.txt"
git -C "$TEST_ROOT/seed" add .env.example app.txt docker-compose.yml scripts/backup.sh
git -C "$TEST_ROOT/seed" commit -m initial >/dev/null
git -C "$TEST_ROOT/seed" remote add origin "$TEST_ROOT/origin.git"
git -C "$TEST_ROOT/seed" push -u origin master >/dev/null

git clone "$TEST_ROOT/origin.git" "$TEST_ROOT/host/mangashelf" >/dev/null
printf 'configured=yes\n' > "$TEST_ROOT/host/mangashelf/.env"
INITIAL_COMMIT="$(git -C "$TEST_ROOT/host/mangashelf" rev-parse HEAD)"

printf 'second\n' > "$TEST_ROOT/seed/app.txt"
git -C "$TEST_ROOT/seed" add app.txt
git -C "$TEST_ROOT/seed" commit -m second >/dev/null
git -C "$TEST_ROOT/seed" push origin master >/dev/null
SECOND_COMMIT="$(git -C "$TEST_ROOT/seed" rev-parse HEAD)"

export PATH="$TEST_ROOT/bin:$PATH"
export FAKE_DEPLOY_LOG="$TEST_ROOT/deploy.log"
export MANGASHELF_ROOT="$TEST_ROOT/host"

"$DEPLOY_SCRIPT" master > "$TEST_ROOT/deploy.out" 2>&1

[[ "$(git -C "$TEST_ROOT/host/mangashelf" rev-parse HEAD)" == "$SECOND_COMMIT" ]] ||
    fail 'successful deployment did not update to the remote commit'
assert_file_contains "$FAKE_DEPLOY_LOG" "backup $INITIAL_COMMIT"
assert_file_contains "$FAKE_DEPLOY_LOG" 'docker compose up -d --build --remove-orphans --wait --wait-timeout 180'
assert_file_contains "$FAKE_DEPLOY_LOG" 'docker compose exec -T backend wget -qO- http://localhost:8080/actuator/health'
assert_file_contains "$FAKE_DEPLOY_LOG" 'docker compose exec -T frontend wget -qO- http://localhost/'
assert_file_contains "$TEST_ROOT/host/.mangashelf-last-deploy" 'status=successful'
assert_file_contains "$TEST_ROOT/host/.mangashelf-last-deploy" "previous_commit=$INITIAL_COMMIT"
assert_file_contains "$TEST_ROOT/host/.mangashelf-last-deploy" "deployed_commit=$SECOND_COMMIT"

"$DEPLOY_SCRIPT" --rollback > "$TEST_ROOT/rollback.out" 2>&1
[[ "$(git -C "$TEST_ROOT/host/mangashelf" rev-parse HEAD)" == "$INITIAL_COMMIT" ]] ||
    fail 'rollback did not restore the previous commit'
assert_file_contains "$TEST_ROOT/host/.mangashelf-last-deploy" 'status=rolled_back'

# Return to the current branch before testing a failed deployment.
"$DEPLOY_SCRIPT" master > "$TEST_ROOT/redeploy.out" 2>&1

printf 'third\n' > "$TEST_ROOT/seed/app.txt"
git -C "$TEST_ROOT/seed" add app.txt
git -C "$TEST_ROOT/seed" commit -m third >/dev/null
git -C "$TEST_ROOT/seed" push origin master >/dev/null
THIRD_COMMIT="$(git -C "$TEST_ROOT/seed" rev-parse HEAD)"

if FAKE_DOCKER_FAIL_UP=1 "$DEPLOY_SCRIPT" master \
    > "$TEST_ROOT/failed.out" 2>&1; then
    fail 'deployment succeeded even though Docker reported a build failure'
fi
assert_file_contains "$TEST_ROOT/failed.out" "$DEPLOY_SCRIPT --rollback"
assert_file_contains "$TEST_ROOT/host/.mangashelf-last-deploy" 'status=pending'

"$DEPLOY_SCRIPT" --rollback > "$TEST_ROOT/failed-rollback.out" 2>&1
[[ "$(git -C "$TEST_ROOT/host/mangashelf" rev-parse HEAD)" == "$SECOND_COMMIT" ]] ||
    fail 'rollback after a failed deployment did not restore the previous commit'

# A deployment containing a Flyway migration must not silently roll code back.
"$DEPLOY_SCRIPT" master > "$TEST_ROOT/third-deploy.out" 2>&1
[[ "$(git -C "$TEST_ROOT/host/mangashelf" rev-parse HEAD)" == "$THIRD_COMMIT" ]] ||
    fail 'deployment did not return to the latest master commit'

mkdir -p -- "$TEST_ROOT/seed/backend/src/main/resources/db/migration"
printf '%s\n' 'SELECT 1;' > \
    "$TEST_ROOT/seed/backend/src/main/resources/db/migration/V99__test.sql"
git -C "$TEST_ROOT/seed" add backend/src/main/resources/db/migration/V99__test.sql
git -C "$TEST_ROOT/seed" commit -m migration >/dev/null
git -C "$TEST_ROOT/seed" push origin master >/dev/null

"$DEPLOY_SCRIPT" master > "$TEST_ROOT/migration-deploy.out" 2>&1
assert_file_contains "$TEST_ROOT/host/.mangashelf-last-deploy" 'migration_changed=1'
if "$DEPLOY_SCRIPT" --rollback > "$TEST_ROOT/migration-rollback.out" 2>&1; then
    fail 'automatic rollback was allowed after a database migration'
fi
assert_file_contains "$TEST_ROOT/migration-rollback.out" \
    'Automatic code rollback is disabled because this deploy changed database migrations.'

# The version committed in the repository must also resolve the project root
# correctly without MANGASHELF_ROOT.
cp -- "$DEPLOY_SCRIPT" "$TEST_ROOT/host/mangashelf/deploy.sh"
chmod +x -- "$TEST_ROOT/host/mangashelf/deploy.sh"
unset MANGASHELF_ROOT
"$TEST_ROOT/host/mangashelf/deploy.sh" master > "$TEST_ROOT/in-repo.out" 2>&1
assert_file_contains "$TEST_ROOT/host/.mangashelf-last-deploy" 'status=successful'

printf 'Deploy script tests passed.\n'
