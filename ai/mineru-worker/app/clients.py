"""file-service / resume-service 内部接口客户端。"""
import logging
from typing import Any

import httpx

logger = logging.getLogger(__name__)


class InternalApiError(RuntimeError):
    """内部接口返回非 0 业务码或 HTTP 错误。"""


class InternalClient:
    def __init__(self, base_url: str, token: str, timeout: float):
        self._client = httpx.Client(
            base_url=base_url,
            timeout=timeout,
            headers={"X-Internal-Token": token},
        )

    def close(self) -> None:
        self._client.close()

    def _data(self, response: httpx.Response) -> Any:
        response.raise_for_status()
        body = response.json()
        if body.get("code") != "0":
            raise InternalApiError(
                f"{response.request.url} 返回 {body.get('code')}: {body.get('message')}"
            )
        return body.get("data")


class FileServiceClient(InternalClient):
    """对象存储统一走 file-service，worker 不持有 MinIO 凭据。"""

    def meta(self, file_id: int) -> dict:
        return self._data(self._client.get(f"/internal/files/{file_id}/meta"))

    def presign_download(self, file_id: int) -> dict:
        return self._data(
            self._client.post("/internal/files/presign/download", json={"fileId": file_id})
        )

    def presign_upload(self, file_name: str, size: int, mime: str, sha256: str,
                       biz_type: str = "PARSE_ARTIFACT", biz_id: str | None = None,
                       uploader_id: int = 0) -> dict:
        payload = {
            "fileName": file_name,
            "bizType": biz_type,
            "bizId": biz_id,
            "mime": mime,
            "size": size,
            "sha256": sha256,
            "uploaderId": uploader_id,
        }
        return self._data(
            self._client.post("/internal/files/presign/upload", json=payload)
        )

    def complete(self, file_id: int, sha256: str) -> dict:
        return self._data(
            self._client.post(f"/internal/files/{file_id}/complete", json={"sha256": sha256})
        )

    def upload_bytes(self, url: str, content: bytes, mime: str) -> None:
        response = httpx.put(
            url,
            content=content,
            headers={"Content-Type": mime},
            timeout=120.0,
            follow_redirects=True,
        )
        response.raise_for_status()


class ResumeServiceClient(InternalClient):
    def parse_completed(self, resume_id: int, task_no: str, markdown_file_id: int,
                        parser_version: str, chunk_count: int = 0) -> dict:
        payload = {
            "taskNo": task_no,
            "markdownFileId": markdown_file_id,
            "parserVersion": parser_version,
            "chunkCount": chunk_count,
        }
        return self._data(
            self._client.post(f"/internal/resumes/{resume_id}/parse-completed", json=payload)
        )

    def parse_failed(self, resume_id: int, task_no: str, reason: str) -> dict:
        payload = {"taskNo": task_no, "reason": reason[:512]}
        return self._data(
            self._client.post(f"/internal/resumes/{resume_id}/parse-failed", json=payload)
        )
