"""云端 LLM 提供者（OpenAI 兼容）。

DeepSeek / 通义等只要兼容 OpenAI 的 /v1/chat/completions 即可使用；
换厂商只改 CLOUD_BASE_URL + CLOUD_MODEL + CLOUD_API_KEY。
"""
from openai import OpenAI

from app.config import settings


class CloudLlm:
    provider = "cloud"

    def __init__(self) -> None:
        self.model = settings.cloud_model
        self.client = OpenAI(
            base_url=settings.cloud_base_url,
            api_key=settings.cloud_api_key,
            timeout=settings.ai_timeout_seconds,
        )

    def chat(self, messages: list[dict], **kwargs):
        response = self.client.chat.completions.create(
            model=self.model,
            messages=messages,
            **kwargs,
        )
        if kwargs.get("stream"):
            # 流式：调用方直接消费 stream；路由层无法在中途切换档位
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
