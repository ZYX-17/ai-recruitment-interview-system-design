#!/usr/bin/env bash
# CI：空库 Flyway 迁移（auth / interview / notify）
# 迁移脚本尚未落地时（P1-6 之前）跳过，不阻塞流水线。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"

declare -A DB=(
  [auth]="recruit_auth"
  [interview]="recruit_interview"
  [notify]="recruit_notify"
)

migrated=0
for svc in auth interview notify; do
  dir="backend/${svc}-service/src/main/resources/db/migration"
  if ! compgen -G "${dir}/V*.sql" > /dev/null; then
    echo ">>> ${svc}: 暂无 ${dir}/V*.sql，跳过"
    continue
  fi

  db="${DB[$svc]}"
  echo ">>> Flyway migrate ${svc} -> ${db}"
  mvn -B -ntp -f backend/ci-flyway/pom.xml flyway:migrate \
    "-Dflyway.url=jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${db}?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true" \
    "-Dflyway.user=${MYSQL_USER}" \
    "-Dflyway.password=${MYSQL_PASSWORD}" \
    "-Dflyway.locations=filesystem:${ROOT}/${dir}" \
    "-Dflyway.validateOnMigrate=true" \
    "-Dflyway.cleanDisabled=true"
  migrated=$((migrated + 1))
done

if [ "$migrated" -eq 0 ]; then
  echo "::notice::backend/*/src/main/resources/db/migration 下暂无 V*.sql（P1-6 起落地），本次空库迁移跳过。"
fi
