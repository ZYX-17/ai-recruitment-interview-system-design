"""ai-service 入口（FastAPI，端口 9010）。

AI 能力统一收口：所有 LLM/ASR/Embedding 调用经此编排（鉴权/配额/超时/降级/审计），
不允许业务 Controller 绕过本服务直连模型（架构原则 2）。
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.routers import tasks

app = FastAPI(title="recruit-ai-service", version="3.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(tasks.router)


@app.get("/health")
async def health():
    return {
        "status": "ok",
        "service": settings.app_name,
        "port": settings.port,
        "llm_primary": settings.vllm_model,
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=settings.port, reload=True)
