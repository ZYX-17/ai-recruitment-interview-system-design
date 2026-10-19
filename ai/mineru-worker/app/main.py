"""mineru-worker 入口（FastAPI，端口 8002）：MinerU 文档解析（低频重任务）。

解析流水线（简历域，Owner：B）：上传 → MinIO → MQ 任务 → MinerU 解析 →
结构化 Markdown 入 MySQL → 分块（表格独立切块+表头重复+标题路径前缀）→ BGE → ES 索引。
显存错峰：与 vLLM/FunASR 按需调度，防 OOM。
"""
import os

from fastapi import FastAPI

MINERU_MOCK = os.getenv("MINERU_MOCK", "true").lower() == "true"

app = FastAPI(title="recruit-mineru-worker", version="3.1.0")


@app.get("/health")
async def health():
    return {"status": "ok", "service": "mineru-worker", "mock": MINERU_MOCK}


@app.post("/parse")
async def parse(task: dict):
    """解析任务：{fileKey, resumeId, taskNo} → 结构化 Markdown + structured_json。

    TODO(W3~4)：MinerU 接入（magic-pdf），失败任务 retry≤3 转死信走补偿页。
    """
    if MINERU_MOCK:
        return {"code": "0", "data": {"mock": True, "markdown": "# [MOCK] 简历结构化结果", "taskNo": task.get("taskNo")}}
    return {"code": "AIS5001", "message": "mineru not installed"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=8002, reload=True)
