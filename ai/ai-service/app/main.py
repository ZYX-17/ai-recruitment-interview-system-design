"""ai-service 入口（FastAPI，端口 9010）。

AI 能力统一收口：所有 LLM/ASR/Embedding 调用经此编排（鉴权/配额/超时/降级/审计），
不允许业务 Controller 绕过本服务直连模型（架构原则 2）。
"""
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import text

from app.config import settings
from app.db import SessionLocal, init_db
from app.routers import tasks


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 数据库不可用时只告警：任务接口会失败，但健康检查仍可访问
    init_db()
    yield


app = FastAPI(title="recruit-ai-service", version="3.1.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(tasks.router)


@app.get("/health")
@app.get("/api/ai/health")
async def health():
    db_ready = False
    try:
        with SessionLocal() as db:
            db.execute(text("SELECT 1"))
        db_ready = True
    except Exception:
        db_ready = False
    return {
        "status": "ok" if db_ready else "degraded",
        "service": settings.app_name,
        "port": settings.port,
        "db_ready": db_ready,
        "ai_mode": settings.ai_mode,
        "cloud_model": settings.cloud_model,
        "local_model": settings.local_llm_model,
        "local_base_url": settings.local_llm_base_url,
        "internal_token_required": bool(settings.internal_token),
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=settings.port, reload=True)
