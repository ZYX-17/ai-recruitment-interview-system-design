"""本地 LLM 提供者（OpenAI 兼容，单独文件，便于替换）。

本地模型不直接耦合具体引擎，只要它暴露 OpenAI 兼容的 /v1/chat/completions：
- 默认 Ollama:
  LOCAL_LLM_BASE_URL=http://127.0.0.1:11434/v1
  LOCAL_LLM_MODEL=qwen2.5:7b
- 可选 vLLM（有 GPU 时）:
  LOCAL_LLM_BASE_URL=http://127.0.0.1:8000/v1
  LOCAL_LLM_MODEL=Qwen2.5-7B-Instruct-AWQ
换本地模型只改环境变量，不动路由/业务代码。
"""
from openai import OpenAI

from app.config import settings


class LocalLlm:
    provider = "local"

    def __init__(self) -> None:
        self.model = settings.local_llm_model
        self.client = OpenAI(
            base_url=settings.local_llm_base_url,
            api_key=settings.local_llm_api_key or "EMPTY",
            timeout=settings.ai_local_timeout_seconds,
        )

    def chat(self, messages: list[dict], **kwargs):
        response = self.client.chat.completions.create(
            model=self.model,
            messages=messages,
            **kwargs,
        )
        if kwargs.get("stream"):
            return {
                "provider": self.provider,
                "model": self.model,
                "content": None,
                "stream": response,
            }
        return {
            "provider": self.provider,
            "model": self.model,
            "content": response.choices[0].message.content,
            "raw": response,
        }
