"""ai-service 配置（pydantic-settings，环境变量注入；密钥不入库/不入仓）。"""
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_name: str = "ai-service"
    port: int = 9010

    # 数据库（recruit_ai）
    mysql_host: str = "127.0.0.1"
    mysql_port: int = 3306
    mysql_user: str = "root"
    mysql_password: str = "root"
    mysql_db: str = "recruit_ai"

    redis_host: str = "127.0.0.1"
    redis_port: int = 6379

    # LLM 档位（OpenAI 兼容端点；四级降级见 services/llm_router.py）
    vllm_base_url: str = "http://127.0.0.1:8000/v1"     # GPU 节点 vLLM（主档）
    vllm_model: str = "Qwen2.5-7B-Instruct-AWQ"
    cloud_base_url: str = "https://api.deepseek.com/v1"  # 云端备档
    cloud_api_key: str = ""                              # DeepSeek/通义 API Key（环境变量注入）
    cloud_model: str = "deepseek-chat"
    ollama_base_url: str = "http://127.0.0.1:11434/v1"   # 无 GPU 档
    ollama_model: str = "qwen3:1.7b"

    # 内部依赖
    ai_worker_url: str = "http://127.0.0.1:8001"         # FunASR / BGE
    mineru_worker_url: str = "http://127.0.0.1:8002"     # MinerU

    # JWT 公钥（校验 room 签发的 scope=asr 令牌）
    jwt_public_key_path: str = ""


settings = Settings()
