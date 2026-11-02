#!/usr/bin/env bash
# 本地开发：生成 RS256 双令牌密钥（docs/4 §7.1.2）。
#
# 用法：
#   ./scripts/dev/gen-jwt-keys.sh                 # 生成到 infra/secrets，kid=k1
#   KID=k2 ./scripts/dev/gen-jwt-keys.sh          # 轮换：新 kid 另存一份
#   FORCE=1 ./scripts/dev/gen-jwt-keys.sh         # 覆盖同 kid 的已有文件
#
# 输出目录被 .gitignore 的 *.pem 规则排除；私钥只应挂给 auth-service，
# 网关与业务服务只挂公钥。轮换时旧公钥继续留在 previous-public-keys，至少保留一个 Access TTL。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="${OUT_DIR:-${ROOT}/infra/secrets}"
KID="${KID:-k1}"

if ! command -v openssl > /dev/null 2>&1; then
  echo "错误：找不到 openssl，请先安装（Windows 可用 Git Bash 自带的 openssl）" >&2
  exit 1
fi

mkdir -p "${OUT_DIR}"
PRIVATE_KEY="${OUT_DIR%/}/jwt-private.pem"
PUBLIC_KEY="${OUT_DIR%/}/jwt-public.pem"

if [[ "${KID}" != "k1" ]]; then
  PRIVATE_KEY="${OUT_DIR%/}/jwt-private-${KID}.pem"
  PUBLIC_KEY="${OUT_DIR%/}/jwt-public-${KID}.pem"
fi

if [[ -e "${PRIVATE_KEY}" && "${FORCE:-0}" != "1" ]]; then
  echo "错误：${PRIVATE_KEY} 已存在。轮换请换 KID，或确认覆盖用 FORCE=1。" >&2
  exit 1
fi

umask 077
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "${PRIVATE_KEY}" 2>/dev/null
openssl rsa -pubout -in "${PRIVATE_KEY}" -out "${PUBLIC_KEY}" 2>/dev/null
chmod 600 "${PRIVATE_KEY}"
chmod 644 "${PUBLIC_KEY}"

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*)
    echo "提示：Git Bash 下 chmod 不映射到 Windows ACL，私钥请放在仅本人可读的目录。" >&2
    ;;
esac

echo "已生成 kid=${KID}："
echo "  私钥（仅 auth-service）：${PRIVATE_KEY}"
echo "  公钥（网关/业务服务）：  ${PUBLIC_KEY}"
echo
echo "本地启动 auth-service 前设置："
echo "  export JWT_PRIVATE_KEY_PATH='${PRIVATE_KEY}'"
echo "  export JWT_PUBLIC_KEY_PATH='${PUBLIC_KEY}'"
echo "  export JWT_KEY_ID='${KID}'"
echo
echo "轮换提示：把旧公钥加入 recruit.jwt.previous-public-keys（kid -> 路径），"
echo "旧公钥至少保留一个 Access TTL 后再移除。"
