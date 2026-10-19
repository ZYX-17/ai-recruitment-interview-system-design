"""ai-worker 入口（FastAPI，端口 8001）：FunASR 流式 ASR + BGE Embedding。

ASR 容量契约（开发文档 5.4）：音频来源=参会人浏览器本地麦克风
（AudioWorklet 16kHz/16bit/单声道 PCM，100~200ms/帧）→ Nginx WSS /api/ai/v1/asr/{sessionId}
直达本服务（scope=asr 5min 短时令牌，room 签发、本服务校验）；每参会人一条流
（1v1=2 路，1v5=6 路）；单实例目标 ≥20 路，超出回 ASR_OVERLOAD 降级会后补转写。
"""
import os

from fastapi import FastAPI, WebSocket, WebSocketDisconnect

ASR_MOCK = os.getenv("ASR_MOCK", "true").lower() == "true"  # 无 FunASR 时走 mock

app = FastAPI(title="recruit-ai-worker", version="3.1.0")


@app.get("/health")
async def health():
    return {"status": "ok", "service": "ai-worker", "asr_mock": ASR_MOCK, "capacity_target": 20}


@app.websocket("/ws/asr/{session_id}")
async def asr_stream(ws: WebSocket, session_id: str):
    """流式 ASR。帧协议（开发文档 7.x）：
    客户端 → 服务端：{"type":"start","format":"pcm","sampleRate":16000} | 二进制 PCM 帧 | {"type":"stop"}
    服务端 → 客户端：{"type":"partial","text":...} | {"type":"final","segmentNo":n,"startMs":..,"endMs":..,"text":..} | {"type":"error","code":"ASR_OVERLOAD"}
    TODO(W1)：FunASR paraformer-zh-streaming 接入 + 并发压测（≥20 路校准）。
    """
    # TODO(W1)：校验 scope=asr 短时令牌（首帧或 query 携带）
    await ws.accept()
    if ASR_MOCK:
        await ws.send_json({"type": "partial", "text": "[ASR mock] 字幕链路占位", "sessionId": session_id})
    try:
        while True:
            message = await ws.receive()
            if message.get("bytes"):
                # PCM 音频帧 → FunASR 流式推理（骨架期丢弃）
                pass
            elif message.get("text"):
                import json

                frame = json.loads(message["text"])
                if frame.get("type") == "stop":
                    await ws.send_json({"type": "final", "segmentNo": 0, "text": "[ASR mock] final", "sessionId": session_id})
                    break
    except WebSocketDisconnect:
        pass
    finally:
        await ws.close()


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=8001, reload=True)
