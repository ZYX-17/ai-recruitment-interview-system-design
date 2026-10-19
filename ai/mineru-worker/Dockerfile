# mineru-worker（B）— MinerU 文档解析，端口 8002
# 当前 requirements.txt 为 mock 档依赖；GPU 节点需取消 magic-pdf[full] 注释后重建（镜像较大）。
# 构建：docker build -t recruit/mineru-worker ./ai/mineru-worker
FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

EXPOSE 8002
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8002"]
