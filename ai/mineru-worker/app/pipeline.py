"""resume.received 处理链路：下载原件 → 解析 → 上传 Markdown → 回调 resume-service。"""
import hashlib
import json
import logging
import re
from pathlib import Path

import httpx

from app.clients import FileServiceClient, ResumeServiceClient
from app.config import Settings
from app.parser import parse_document

logger = logging.getLogger(__name__)

MARKDOWN_MIME = "text/markdown"


def handle_message(body: str, settings: Settings) -> None:
    """消费 MQ 消息（EventEnvelope JSON）。"""
    try:
        envelope = json.loads(body)
    except json.JSONDecodeError as exc:
        logger.warning("消息不是合法 JSON，丢弃: %s", exc)
        return

    if envelope.get("eventType") != "resume.received":
        logger.info("忽略非 resume.received 事件: %s", envelope.get("eventType"))
        return

    payload = envelope.get("payload") or {}
    resume_id = payload.get("resumeId")
    task_no = payload.get("taskNo")
    file_id = payload.get("fileId")
    if not resume_id or not task_no or not file_id:
        logger.warning("resume.received 缺少 resumeId/taskNo/fileId: %s", payload)
        return

    process_task(int(resume_id), str(task_no), int(file_id), settings)


def process_task(resume_id: int, task_no: str, file_id: int, settings: Settings) -> bool:
    """执行一次解析；失败时把原因回写 resume-service，避免任务停在 RUNNING。

    @return True=解析并回调成功；False=已回写失败状态（消息可安全 ack）
    """
    file_client = FileServiceClient(
        settings.file_service_url, settings.internal_token, settings.http_timeout_seconds
    )
    resume_client = ResumeServiceClient(
        settings.resume_service_url, settings.internal_token, settings.http_timeout_seconds
    )
    try:
        meta = file_client.meta(file_id)
        if meta.get("status") != "OK":
            raise RuntimeError(f"原文件未完成上传: {meta.get('status')}")
        if str(meta.get("bizType", "")).upper() != "RESUME":
            raise RuntimeError(f"文件业务类型不是 RESUME: {meta.get('bizType')}")

        file_name = meta.get("fileName") or f"{resume_id}.bin"
        mime = meta.get("mime") or "application/octet-stream"

        download_url = file_client.presign_download(file_id)["url"]
        with httpx.Client(timeout=120.0, follow_redirects=True) as http:
            response = http.get(download_url)
            response.raise_for_status()
            content = response.content

        work_dir = Path(settings.work_dir)
        work_dir.mkdir(parents=True, exist_ok=True)
        source = work_dir / f"{resume_id}-{_safe_name(file_name)}"
        source.write_bytes(content)

        markdown, parser_version = parse_document(source, file_name, mime, settings)
        markdown_bytes = markdown.encode("utf-8")
        sha256 = hashlib.sha256(markdown_bytes).hexdigest()

        artifact_name = f"resume-{resume_id}-{task_no}.md"
        presign = file_client.presign_upload(
            file_name=artifact_name,
            size=len(markdown_bytes),
            mime=MARKDOWN_MIME,
            sha256=sha256,
            biz_type="PARSE_ARTIFACT",
            biz_id=str(resume_id),
            uploader_id=settings.mineru_uploader_id,
        )
        markdown_file_id = int(presign["fileId"])
        if not presign.get("instantUpload"):
            file_client.upload_bytes(presign["uploadUrl"], markdown_bytes, MARKDOWN_MIME)
            file_client.complete(markdown_file_id, sha256)

        resume_client.parse_completed(resume_id, task_no, markdown_file_id, parser_version)
        logger.info(
            "解析完成 resumeId=%s taskNo=%s markdownFileId=%s",
            resume_id, task_no, markdown_file_id,
        )
        return True
    except Exception as exc:
        reason = (str(exc) or exc.__class__.__name__)[:512]
        logger.exception("解析失败 resumeId=%s taskNo=%s: %s", resume_id, task_no, reason)
        try:
            resume_client.parse_failed(resume_id, task_no, reason)
        except Exception:
            logger.exception("回写解析失败状态也失败 resumeId=%s taskNo=%s", resume_id, task_no)
            # 交给消费者 nack 到 DLQ，避免消息静默丢失
            raise
        return False
    finally:
        file_client.close()
        resume_client.close()


def _safe_name(file_name: str) -> str:
    cleaned = re.sub(r"[^0-9A-Za-z._\-一-鿿]", "_", file_name).strip("._")
    return (cleaned or "resume.bin")[:120]
