#!/usr/bin/env bash
# 一键回滚：./scripts/rollback.sh <service> <tag>。回滚后自动 smoke 验证。
set -e
SVC="${1:?usage: rollback.sh <service> <tag>}"
TAG="${2:?usage: rollback.sh <service> <tag>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "rollback $SVC -> $TAG"
# TODO(W14)：按镜像 tag 回滚（docker tag / compose up 指定镜像），Flyway 只向前、代码兼容旧 schema
docker compose -f "$ROOT/infra/docker-compose.base.yml" -f "$ROOT/infra/docker-compose.app.yml" up -d "$SVC"

"$ROOT/scripts/smoke.sh"
echo "rollback done"
