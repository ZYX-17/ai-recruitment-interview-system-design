"""mineru-worker 入口（FastAPI，端口 8002）：MinerU 文档解析（低频重任务）。

链路：resume.received → 下载原件 → mock/MinerU 解析 → Markdown 上传 file-service
→ 回调 resume-service（parse-completed / parse-failed）。

显存错峰：与 vLLM/FunASR 按需调度，防 OOM；本地默认 MINERU_MOCK=true，
无 GPU、无 MinerU 依赖也能跑通整条链路。
"""
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException

from app.config import settings
from app.consumer import ResumeConsumer
from app.pipeline import process_task

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
logger = logging.getLogger(__name__)

consumer: ResumeConsumer | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global consumer
    if settings.mq_enabled:
        consumer = ResumeConsumer(settings)
        consumer.start()
    else:
        logger.info("MQ_ENABLED=false，mineru-worker 不启动消费者")
    yield
    if consumer is not None:
        consumer.stop()


app = FastAPI(title="recruit-mineru-worker", version="3.1.0", lifespan=lifespan)


@app.get("/health")
async def health():
    return {
        "status": "ok",
        "service": "mineru-worker",
        "mock": settings.mineru_mock,
        "parser_mode": settings.parser_mode,
        "queue": settings.mineru_queue,
        "consumer_alive": bool(consumer and consumer.is_alive()),
    }


@app.post("/parse")
def parse(task: dict):
    """手工触发一次解析：{resumeId, taskNo, fileId}，便于本地联调。"""
    success = process_task(
        int(task["resumeId"]),
        str(task["taskNo"]),
        int(task["fileId"]),
        settings,
    )
    if not success:
        raise HTTPException(status_code=500, detail="解析失败，原因已回写 resume-service")
    return {"code": "0", "message": "parsed", "data": {"taskNo": task["taskNo"]}}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=settings.port, reload=True)
