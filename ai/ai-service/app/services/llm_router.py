"""LLM 路由：云端优先 → 本地 OpenAI 兼容模型 → Mock（可选）。

- 云端不可用（断网/超时/限流/5xx）时自动切本地模型；
- 本地模型调用单独封装在 local_llm.py，vLLM/Ollama 只改 LOCAL_LLM_* 配置；
- 认证/参数错误不降级，避免掩盖配置问题；
- stream=True 时无法中途切换，固定走云端（调用方需自行处理本地档）。

调用纪律：所有调用记 ai_call_log（trace/capability/model/prompt_version/tokens/latency/status）；
AI 输出一律草稿，经 human_review 确认才写入业务正式状态。
"""
import logging

from openai import (
    APIConnectionError,
    APIStatusError,
    APITimeoutError,
    InternalServerError,
    RateLimitError,
)

from app.config import settings
from app.services.cloud_llm import CloudLlm
from app.services.local_llm import LocalLlm
from app.services.mock_llm import MockLlm

logger = logging.getLogger(__name__)

_FALLBACK_ERRORS = (
    APIConnectionError,
    APITimeoutError,
    RateLimitError,
    InternalServerError,
)


class LlmRouter:
    """按 AI_MODE 选择档位：auto/cloud/local/mock。"""

    TIERS = ("cloud", "local", "mock")

    def __init__(self) -> None:
        self._cloud: CloudLlm | None = None
        self._local: LocalLlm | None = None
        self._mock = MockLlm()

    @property
    def cloud(self) -> CloudLlm:
        if self._cloud is None:
            self._cloud = CloudLlm()
        return self._cloud

    @property
    def local(self) -> LocalLlm:
        if self._local is None:
            self._local = LocalLlm()
        return self._local

    def chat(self, messages: list[dict], tier: str | None = None, **kwargs):
        """调用 LLM。

        tier 为空时读取 AI_MODE；auto 表示云端优先、失败切本地、最后 Mock。
        返回统一 dict：{provider, model, content, raw}；流式返回 stream。
        """
        mode = (tier or settings.ai_mode or "auto").lower()

        if mode == "mock":
            return self._mock.chat(messages, **kwargs)
        if mode == "cloud":
            return self.cloud.chat(messages, **kwargs)
        if mode == "local":
            return self.local.chat(messages, **kwargs)
        if mode not in ("auto", "cloud-first", "cloud_first"):
            raise ValueError(f"unknown AI_MODE: {mode}")

        # 流式无法在中途切换；调用方若需要严格兜底，请使用非流式
        if kwargs.get("stream"):
            if settings.cloud_api_key:
                logger.warning("stream=True: fallback disabled; using cloud LLM")
                return self.cloud.chat(messages, **kwargs)
            logger.warning("stream=True and CLOUD_API_KEY is empty; using local LLM")
            return self.local.chat(messages, **kwargs)

        # 云端优先
        if not settings.cloud_api_key:
            logger.warning("CLOUD_API_KEY is empty; skipping cloud LLM")
        else:
            try:
                return self.cloud.chat(messages, **kwargs)
            except Exception as exc:
                if not self._should_fallback(exc):
                    raise
                logger.warning("cloud LLM unavailable, falling back to local: %s", exc)

        # 本地兜底
        try:
            return self.local.chat(messages, **kwargs)
        except Exception as exc:
            logger.warning("local LLM unavailable: %s", exc)
            if settings.ai_allow_mock:
                return self._mock.chat(messages, **kwargs)
            raise

    @staticmethod
    def _should_fallback(exc: Exception) -> bool:
        """只有网络/超时/限流/5xx 才降级；401/403/400 视为配置错误，直接抛出。"""
        if isinstance(exc, _FALLBACK_ERRORS):
            return True
        if isinstance(exc, APIStatusError) and exc.status_code >= 500:
            return True
        return False


llm_router = LlmRouter()
