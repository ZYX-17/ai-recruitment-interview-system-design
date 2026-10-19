"""LLM 路由器（占位骨架，W1 vLLM 冒烟后完善）。

四级降级链（v3.0 裁决 5）：
  vLLM(Qwen2.5-7B-Instruct-AWQ, GPU 节点) 主
    → 云端 LLM(DeepSeek 主/通义备)
    → Ollama(qwen3:1.7b) 无 GPU 档
    → 静态模板（Mock 档供开发/CI）
四类端点均 OpenAI 兼容：换档只改 base_url + model。
调用纪律：所有调用记 ai_call_log（trace/capability/model/prompt_version/tokens/latency/status）；
AI 输出一律草稿，经 human_review 确认才写入业务正式状态。
"""
from openai import OpenAI

from app.config import settings


class LlmRouter:
    """按优先级选择可用档位，超时/限流/错误自动降级。"""

    TIERS = ("vllm", "cloud", "ollama", "mock")

    def __init__(self) -> None:
        self._clients = {
            "vllm": OpenAI(base_url=settings.vllm_base_url, api_key="EMPTY", timeout=60),
            "cloud": OpenAI(base_url=settings.cloud_base_url, api_key=settings.cloud_api_key, timeout=60),
            "ollama": OpenAI(base_url=settings.ollama_base_url, api_key="ollama", timeout=120),
        }
        self._models = {
            "vllm": settings.vllm_model,
            "cloud": settings.cloud_model,
            "ollama": settings.ollama_model,
        }

    def chat(self, messages: list[dict], tier: str = "vllm", **kwargs):
        """单档调用（骨架）。W1 后实现：try tiers 顺序、熔断、guided JSON Schema、审计落库。"""
        if tier == "mock":
            return {"mock": True, "content": "[MOCK] 静态模板输出", "model": "mock"}
        client = self._clients[tier]
        return client.chat.completions.create(model=self._models[tier], messages=messages, **kwargs)


llm_router = LlmRouter()
