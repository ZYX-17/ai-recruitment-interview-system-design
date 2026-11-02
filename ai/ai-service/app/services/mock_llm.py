"""Mock LLM 提供者：开发/CI 无云 Key、无本地模型时使用。

简历抽取返回一份确定性的合法 JSON，保证 Pydantic 与下游链路可跑通；
其他能力仍返回占位文案。
"""
import json

MOCK_RESUME_EXTRACTION = {
    "name": "张三",
    "phone": "13800000000",
    "email": "zhangsan@example.com",
    "city": "上海",
    "education": "本科",
    "workYears": 5,
    "skills": [
        {"name": "Java", "level": "熟练", "years": 5},
        {"name": "Spring", "level": "熟练", "years": 4},
    ],
    "experiences": [
        {
            "company": "腾讯",
            "title": "Java 开发工程师",
            "startDate": "2019-07",
            "endDate": "2022-06",
            "description": "负责订单系统后端开发",
        }
    ],
    "educations": [
        {
            "school": "某某大学",
            "major": "计算机科学与技术",
            "degree": "本科",
            "startDate": "2015-09",
            "endDate": "2019-06",
        }
    ],
    "projects": [
        {
            "name": "订单中台",
            "role": "后端负责人",
            "description": "订单创建、支付回调与对账",
        }
    ],
    "certificates": ["CET-6"],
    "summary": "5 年 Java 后端开发经验，熟悉 Spring 微服务。",
}


class MockLlm:
    provider = "mock"
    model = "mock"

    def chat(self, messages: list[dict], **kwargs):
        joined = " ".join(str(message.get("content", "")) for message in messages)
        if "resume_extract" in joined:
            content = json.dumps(MOCK_RESUME_EXTRACTION, ensure_ascii=False)
        else:
            content = "[MOCK] 静态模板输出"
        return {
            "provider": self.provider,
            "model": self.model,
            "content": content,
            "raw": None,
            "mock": True,
        }
