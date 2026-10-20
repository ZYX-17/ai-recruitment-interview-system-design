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

    # LLM 路由：cloud 优先 → local（OpenAI 兼容）→ mock（可选）
    ai_mode: str = "auto"              # auto / cloud / local / mock
    ai_allow_mock: bool = True         # 云端+本地都不可用时是否允许 Mock
    ai_timeout_seconds: float = 30.0
    ai_local_timeout_seconds: float = 60.0

    # 云端 LLM（OpenAI 兼容；DeepSeek flash/fast 档）
    cloud_base_url: str = "https://api.deepseek.com/v1"
    cloud_api_key: str = ""            # DeepSeek API Key（环境变量注入）
    cloud_model: str = "deepseek-chat" # DeepSeek flash/fast；如官方模型名不同改这里

    # 本地 LLM（OpenAI 兼容：默认 Ollama；有 GPU 可切 vLLM）
    # Ollama: http://127.0.0.1:11434/v1 + qwen2.5:7b
    # vLLM:   http://127.0.0.1:8000/v1 + Qwen2.5-7B-Instruct-AWQ
    local_llm_base_url: str = "http://127.0.0.1:11434/v1"
    local_llm_model: str = "qwen2.5:7b"
    local_llm_api_key: str = "EMPTY"

    # 内部依赖
    ai_worker_url: str = "http://127.0.0.1:8001"         # FunASR / BGE
    mineru_worker_url: str = "http://127.0.0.1:8002"     # MinerU

    # JWT 公钥（校验 room 签发的 scope=asr 令牌）
    jwt_public_key_path: str = ""


settings = Settings()
