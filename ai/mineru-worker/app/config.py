"""mineru-worker 配置：全部来自环境变量，便于 Docker/本地切换。"""
import os
from dataclasses import dataclass


def _env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _env_int(name: str, default: int) -> int:
    value = os.getenv(name)
    try:
        return int(value) if value is not None else default
    except ValueError:
        return default


def _env_float(name: str, default: float) -> float:
    value = os.getenv(name)
    try:
        return float(value) if value is not None else default
    except ValueError:
        return default


@dataclass(frozen=True)
class Settings:
    app_name: str = os.getenv("APP_NAME", "mineru-worker")
    port: int = _env_int("PORT", 8002)

    # mock：无 GPU/无 MinerU 依赖也能跑通完整链路；mineru：调用 MinerU CLI
    mineru_mock: bool = _env_bool("MINERU_MOCK", True)
    parser_mode: str = os.getenv(
        "PARSER_MODE", "mock" if _env_bool("MINERU_MOCK", True) else "mineru"
    ).strip().lower()
    mineru_cli: str = os.getenv("MINERU_CLI", "mineru")
    mineru_timeout_seconds: int = _env_int("MINERU_TIMEOUT_SECONDS", 600)
    work_dir: str = os.getenv("MINERU_WORK_DIR", "/tmp/mineru")

    # RabbitMQ：队列由 resume-service 声明（带 DLX），worker 只消费不 declare
    rabbitmq_host: str = os.getenv("RABBITMQ_HOST", "127.0.0.1")
    rabbitmq_port: int = _env_int("RABBITMQ_PORT", 5672)
    rabbitmq_user: str = os.getenv("RABBITMQ_USER", "guest")
    rabbitmq_password: str = os.getenv("RABBITMQ_PASSWORD", "guest")
    events_exchange: str = os.getenv("EVENTS_EXCHANGE", "recruit.events")
    mineru_queue: str = os.getenv("MINERU_QUEUE", "mineru.resume-received")
    mq_enabled: bool = _env_bool("MQ_ENABLED", True)

    # 内部服务
    file_service_url: str = os.getenv("FILE_SERVICE_URL", "http://127.0.0.1:8087").rstrip("/")
    resume_service_url: str = os.getenv("RESUME_SERVICE_URL", "http://127.0.0.1:8084").rstrip("/")
    internal_token: str = os.getenv("INTERNAL_TOKEN", "dev-internal-token")
    http_timeout_seconds: float = _env_float("HTTP_TIMEOUT_SECONDS", 30.0)
    # 产物上传人：0 表示系统账号
    mineru_uploader_id: int = _env_int("MINERU_UPLOADER_ID", 0)


settings = Settings()
