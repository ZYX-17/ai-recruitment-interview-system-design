#!/usr/bin/env bash
# 一键部署：./scripts/deploy.sh <service|all>。单服务粒度，目标 1 分钟回滚（见 rollback.sh）。
set -e
SVC="${1:-all}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

case "$SVC" in
  infra)
    docker compose -f "$ROOT/infra/docker-compose.base.yml" up -d ;;
  gpu)
    docker compose -f "$ROOT/infra/docker-compose.gpu.yml" --profile gpu up -d ;;
  all)
    docker compose -f "$ROOT/infra/docker-compose.base.yml" -f "$ROOT/infra/docker-compose.app.yml" up -d --build ;;
  *)
    docker compose -f "$ROOT/infra/docker-compose.base.yml" -f "$ROOT/infra/docker-compose.app.yml" up -d --build "$SVC" ;;
esac

echo "deploy $SVC done"
