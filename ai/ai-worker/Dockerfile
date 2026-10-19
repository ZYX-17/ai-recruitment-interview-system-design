# ai-worker（B）— BGE Embedding + FunASR 流式 ASR，端口 8001
# 当前 requirements.txt 为 mock 档依赖；GPU 节点需取消 funasr/torch/sentence-transformers 注释后重建。
# 构建：docker build -t recruit/ai-worker ./ai/ai-worker
FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

EXPOSE 8001
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
