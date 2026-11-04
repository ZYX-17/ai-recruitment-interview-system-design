# 复制为 scripts/dev/env.local.ps1 后按实际情况修改；env.local.ps1 不属于仓库文件。
$env:MYSQL_HOST = "127.0.0.1"
$env:MYSQL_PORT = "3306"
$env:MYSQL_USER = "root"
$env:MYSQL_PASSWORD = "改成你的密码"

$env:REDIS_HOST = "127.0.0.1"
$env:REDIS_PORT = "6379"
$env:REDIS_PASSWORD = ""

$env:RABBITMQ_HOST = "127.0.0.1"
$env:RABBITMQ_PORT = "5672"
$env:RABBITMQ_USER = "guest"
$env:RABBITMQ_PASSWORD = "guest"

# auth-service 首次启动执行 Flyway seed 时使用；至少 8 位
$env:SEED_ADMIN_PASSWORD = "Admin@12345"
# 服务间内部令牌，auth / interview / notify 必须一致
$env:INTERNAL_TOKEN = "dev-internal-token-please-change"

$env:JWT_KEY_ID = "k1"
$env:JWT_PRIVATE_KEY_PATH = "D:\ai-recruitment-interview-system-design\infra\secrets\jwt-private.pem"
$env:JWT_PUBLIC_KEY_PATH = "D:\ai-recruitment-interview-system-design\infra\secrets\jwt-public.pem"

# interview / notify 通过该地址调用 auth 的内部接口，无需 Nacos
$env:AUTH_SERVICE_URL = "http://127.0.0.1:8081"
# 本地不发邮件，只落站内信
$env:MAIL_ENABLED = "false"
# 本地 HTTP：Refresh Cookie 不加 Secure（便于 curl 调试）
$env:COOKIE_SECURE = "false"
$env:CORS_ALLOWED_ORIGINS = "http://localhost:5173,http://localhost:8088"
