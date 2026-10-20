"""Mock LLM 提供者：开发/CI 无云 Key、无本地模型时使用。"""


class MockLlm:
    provider = "mock"
    model = "mock"

    def chat(self, messages: list[dict], **kwargs):
        return {
            "provider": self.provider,
            "model": self.model,
            "content": "[MOCK] 静态模板输出",
            "raw": None,
            "mock": True,
        }
