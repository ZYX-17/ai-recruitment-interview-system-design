#!/usr/bin/env bash
# 冒烟：A 域 4 服务健康 + 登录 + 菜单（docs/4 §13 P0-8）
#
# 用法：
#   ./scripts/smoke.sh                      # 默认 http://127.0.0.1
#   ./scripts/smoke.sh http://10.0.0.5      # 指定主机（端口固定）
#   SMOKE_USER=admin SMOKE_PASSWORD=*** ./scripts/smoke.sh
#
# 退出码：0 = 全绿；1 = 有失败项。
set -uo pipefail

HOST="${1:-http://127.0.0.1}"
GATEWAY="$HOST:8080"
SMOKE_USER="${SMOKE_USER:-admin}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-}"

fail=0
ok()   { echo "  [OK]   $*"; }
bad()  { echo "  [FAIL] $*"; fail=1; }
info() { echo "  [INFO] $*"; }

http_code() {
  local out
  out="$(curl -s -o /dev/null -w "%{http_code}" --max-time "${2:-5}" "$1" 2>/dev/null)" || true
  printf '%s' "${out:-000}"
}

health() {
  local name="$1" url="$2" code
  code="$(http_code "$url")"
  if [ "$code" = "200" ]; then
    ok "$name health（HTTP 200）"
  else
    bad "$name health → HTTP $code（$url）"
  fi
}

json_get() {
  local json="$1" key="$2"
  if command -v jq >/dev/null 2>&1; then
    printf '%s' "$json" | jq -r --arg k "$key" '.data[$k] // empty' 2>/dev/null
  elif command -v python3 >/dev/null 2>&1; then
    printf '%s' "$json" | python3 -c 'import json,sys
try:
    d = json.load(sys.stdin)
except Exception:
    print("")
    raise SystemExit
print((d.get("data") or {}).get(sys.argv[1], "") or "")' "$key" 2>/dev/null
  else
    printf '%s' "$json" | sed -n "s/.*\"$key\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" | head -n 1
  fi
}

json_code() {
  local json="$1"
  if command -v jq >/dev/null 2>&1; then
    printf '%s' "$json" | jq -r '.code // empty' 2>/dev/null
  elif command -v python3 >/dev/null 2>&1; then
    printf '%s' "$json" | python3 -c 'import json,sys
try:
    d = json.load(sys.stdin)
except Exception:
    print("")
    raise SystemExit
print(d.get("code", "") or "")' 2>/dev/null
  else
    printf '%s' "$json" | sed -n 's/.*"code"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1
  fi
}

menu_count() {
  local json="$1"
  if command -v jq >/dev/null 2>&1; then
    printf '%s' "$json" | jq -r '(.data // []) | length' 2>/dev/null
  elif command -v python3 >/dev/null 2>&1; then
    printf '%s' "$json" | python3 -c 'import json,sys
try:
    d = json.load(sys.stdin)
except Exception:
    print(0)
    raise SystemExit
data = d.get("data")
print(len(data) if isinstance(data, list) else 0)' 2>/dev/null
  else
    printf '%s' "$json" | grep -o '"id"' | wc -l | tr -d ' '
  fi
}

echo "== A 域服务健康 =="
health "api-gateway"       "$GATEWAY/actuator/health"
health "auth-service"      "$HOST:8081/actuator/health"
health "interview-service" "$HOST:8083/actuator/health"
health "notify-service"    "$HOST:8086/actuator/health"

echo "== 其他服务（仅信息，不计入判定）=="
for svc in "resume-service:8084" "room-service:8085" "file-service:8087"; do
  info "${svc%%:*} health → HTTP $(http_code "$HOST:${svc##*:}/actuator/health" 3)"
done
for svc in "ai-service:9010" "ai-worker:8001" "mineru-worker:8002"; do
  info "${svc%%:*} health → HTTP $(http_code "$HOST:${svc##*:}/health" 3)"
done

echo "== 登录 =="
if [ -z "$SMOKE_PASSWORD" ]; then
  bad "未设置 SMOKE_PASSWORD（对应 seed 管理员初始密码），跳过登录与菜单"
  echo
  echo "smoke FAILED"
  exit 1
fi

LOGIN_BODY="$(printf '{"username":"%s","password":"%s"}' "$SMOKE_USER" "$SMOKE_PASSWORD")"
LOGIN_RESP="$(curl -s --max-time 10 -H 'Content-Type: application/json' -d "$LOGIN_BODY" "$GATEWAY/api/auth/login" 2>/dev/null || true)"
LOGIN_CODE="$(json_code "$LOGIN_RESP")"
TOKEN="$(json_get "$LOGIN_RESP" accessToken)"

if [ "$LOGIN_CODE" = "0" ] && [ -n "$TOKEN" ]; then
  ok "登录成功（$SMOKE_USER）"
else
  bad "登录失败（code=${LOGIN_CODE:-none}）：$LOGIN_RESP"
fi

echo "== 菜单 =="
if [ -n "$TOKEN" ]; then
  MENU_RESP="$(curl -s --max-time 10 -H "Authorization: Bearer $TOKEN" "$GATEWAY/api/auth/menus" 2>/dev/null || true)"
  MENU_CODE="$(json_code "$MENU_RESP")"
  if [ "$MENU_CODE" = "0" ]; then
    ok "菜单加载成功（根节点 $(menu_count "$MENU_RESP") 个）"
  else
    bad "菜单加载失败（code=${MENU_CODE:-none}）：$MENU_RESP"
  fi
else
  bad "无 Access Token，跳过菜单校验"
fi

echo
if [ "$fail" -eq 0 ]; then
  echo "smoke PASSED"
else
  echo "smoke FAILED"
fi
exit "$fail"
