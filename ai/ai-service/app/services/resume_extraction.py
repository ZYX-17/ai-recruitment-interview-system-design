"""简历实体抽取：Prompt 版本 + JSON 修复一次 + Pydantic 二次校验 + 调用审计。"""
import json
import logging
import time
from dataclasses import dataclass

from pydantic import ValidationError
from sqlalchemy.orm import Session

from app.config import settings
from app.ids import new_id
from app.models import AiCallLog
from app.prompts.resume_extract import PROMPT_CODE, USER_TEMPLATE
from app.schemas import ResumeExtraction
from app.services.llm_router import LlmRouter, llm_router
from app.services.prompt_service import get_active_prompt

logger = logging.getLogger(__name__)


class InvalidLlmOutput(ValueError):
    """LLM 返回的不是合法 JSON 或不符合 Schema。"""


class ExtractionError(RuntimeError):
    """抽取失败（调用失败或两次 Schema 校验都失败）。"""


@dataclass
class ExtractionOutcome:
    structured: dict
    provider: str
    model: str
    prompt_version: int
    latency_ms: int
    repaired: bool = False


class ResumeExtractionService:
    def __init__(self, router: LlmRouter | None = None) -> None:
        self.router = router or llm_router

    def extract(self, db: Session, text: str, trace_id: str,
                task_no: str | None = None,
                prompt_version: int | None = None) -> ExtractionOutcome:
        prompt = get_active_prompt(db, PROMPT_CODE, prompt_version)
        clipped = (text or "")[: settings.ai_max_input_chars]
        if not clipped.strip():
            raise ExtractionError("简历文本为空")

        messages = [
            {"role": "system", "content": prompt.content},
            {"role": "user", "content": USER_TEMPLATE.format(text=clipped)},
        ]
        started = time.monotonic()
        first_response = None
        repaired = False

        try:
            first_response = self.router.chat(
                messages, response_format={"type": "json_object"})
            structured = self._validate(first_response)
            response = first_response
        except (InvalidLlmOutput, ValidationError, json.JSONDecodeError, ValueError) as first_error:
            repaired = True
            logger.warning("LLM 输出校验失败，尝试修复一次：%s", first_error)
            repair_messages = messages + [
                {"role": "assistant", "content": self._content(first_response) or ""},
                {"role": "user", "content": (
                    "上面的输出不是合法 JSON 或不符合字段定义。"
                    "请只输出修正后的 JSON 对象，不要解释、不要代码块。"
                )},
            ]
            try:
                response = self.router.chat(
                    repair_messages, response_format={"type": "json_object"})
                structured = self._validate(response)
            except Exception as second_error:
                latency = self._elapsed(started)
                provider = (first_response or {}).get("provider", "unknown")
                model = (first_response or {}).get("model", "unknown")
                self._log_call(db, trace_id, task_no, prompt, provider, model,
                               latency, "ERROR", str(second_error))
                raise ExtractionError(f"AI 输出 Schema 校验失败：{second_error}") from second_error
        except Exception as exc:
            latency = self._elapsed(started)
            self._log_call(db, trace_id, task_no, prompt, "unknown", "unknown",
                           latency, "ERROR", str(exc))
            raise ExtractionError(f"LLM 调用失败：{exc}") from exc

        latency = self._elapsed(started)
        provider = response.get("provider", "unknown")
        model = response.get("model", "unknown")
        self._log_call(db, trace_id, task_no, prompt, provider, model,
                       latency, "OK", None, response.get("raw"))
        return ExtractionOutcome(
            structured=structured.model_dump(),
            provider=provider,
            model=model,
            prompt_version=prompt.version,
            latency_ms=latency,
            repaired=repaired,
        )

    def _validate(self, response: dict | None) -> ResumeExtraction:
        content = self._content(response)
        if not content:
            raise InvalidLlmOutput("LLM 返回空内容")
        return ResumeExtraction.model_validate(self._loads_json(content))

    @staticmethod
    def _content(response: dict | None) -> str | None:
        if not response:
            return None
        content = response.get("content")
        return content if isinstance(content, str) else None

    @staticmethod
    def _loads_json(content: str) -> dict:
        text = content.strip()
        if text.startswith("```"):
            text = text.strip("`")
            if text.lower().startswith("json"):
                text = text[4:]
        start, end = text.find("{"), text.rfind("}")
        if start < 0 or end <= start:
            raise InvalidLlmOutput("LLM 输出中没有 JSON 对象")
        return json.loads(text[start:end + 1])

    def _log_call(self, db: Session, trace_id: str, task_no: str | None, prompt,
                  provider: str, model: str, latency_ms: int, status: str,
                  error: str | None, raw=None) -> None:
        usage = getattr(raw, "usage", None)
        log = AiCallLog(
            id=new_id(),
            trace_id=trace_id[:64],
            task_no=task_no,
            capability="LLM",
            provider_name=(provider or "unknown")[:32],
            model=(model or "unknown")[:64],
            prompt_code=prompt.prompt_code,
            prompt_version=prompt.version,
            input_tokens=getattr(usage, "prompt_tokens", None),
            output_tokens=getattr(usage, "completion_tokens", None),
            latency_ms=latency_ms,
            status=status,
            degraded_to=self._degraded_to(provider),
            error_msg=(error or "")[:512] or None,
        )
        try:
            db.add(log)
            db.commit()
        except Exception:
            db.rollback()
            logger.exception("写 ai_call_log 失败（不影响主流程）")

    @staticmethod
    def _degraded_to(provider: str) -> str | None:
        if provider == "mock" and settings.ai_mode != "mock":
            return "mock"
        if provider == "local" and settings.cloud_api_key and settings.ai_mode in (
                "auto", "cloud-first", "cloud_first"):
            return "local"
        return None

    @staticmethod
    def _elapsed(started: float) -> int:
        return int((time.monotonic() - started) * 1000)
