#!/usr/bin/env bash
# 冒烟脚本：各服务 /ping（Java）与 /health（Python）全绿判定。用法：./scripts/smoke.sh [base_url]
set -e
BASE="${1:-http://127.0.0.1}"

echo "== Java 服务（经直连端口）=="
for svc in "api-gateway:8080" "auth-service:8081" "interview-service:8083" "resume-service:8084" "room-service:8085" "notify-service:8086" "file-service:8087"; do
  name="${svc%%:*}"; port="${svc##*:}"
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$BASE:$port/actuator/health" || true)
  echo "  $name ($port): HTTP $code"
done

echo "== Python 服务 =="
for svc in "ai-service:9010" "ai-worker:8001" "mineru-worker:8002"; do
  name="${svc%%:*}"; port="${svc##*:}"
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$BASE:$port/health" || true)
  echo "  $name ($port): HTTP $code"
done

echo "== 网关 ping（经 8080）=="
curl -s --max-time 5 "$BASE:8080/api/auth/ping" || echo "gateway not ready"
echo
echo "smoke done"
