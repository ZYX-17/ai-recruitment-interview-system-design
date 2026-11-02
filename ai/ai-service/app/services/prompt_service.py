"""Prompt 版本读取与默认种子。"""
import logging
from types import SimpleNamespace

from sqlalchemy.orm import Session

from app.config import settings
from app.ids import new_id
from app.models import PromptVersion
from app.prompts.resume_extract import (
    MODEL_HINT,
    PROMPT_CODE,
    PROMPT_VERSION,
    SYSTEM_PROMPT,
)
from app.schemas import ResumeExtraction

logger = logging.getLogger(__name__)


def default_prompt() -> SimpleNamespace:
    return SimpleNamespace(
        prompt_code=PROMPT_CODE,
        version=PROMPT_VERSION,
        content=SYSTEM_PROMPT,
        response_schema=ResumeExtraction.model_json_schema(),
        model_hint=MODEL_HINT,
    )


def ensure_default_prompts(db: Session) -> None:
    """首次启动写入 v1；已有版本不覆盖。"""
    exists = (
        db.query(PromptVersion)
        .filter(PromptVersion.prompt_code == PROMPT_CODE,
                PromptVersion.version == PROMPT_VERSION)
        .first()
    )
    if exists is not None:
        return
    db.add(PromptVersion(
        id=new_id(),
        prompt_code=PROMPT_CODE,
        version=PROMPT_VERSION,
        content=SYSTEM_PROMPT,
        response_schema=ResumeExtraction.model_json_schema(),
        model_hint=MODEL_HINT or settings.cloud_model,
        status="ACTIVE",
    ))
    db.commit()
    logger.info("已写入默认 Prompt %s v%s", PROMPT_CODE, PROMPT_VERSION)


def get_active_prompt(db: Session, code: str = PROMPT_CODE, version: int | None = None):
    """按版本读取；未指定时取 ACTIVE 的最新版本，数据库不可用时回退内置 Prompt。"""
    try:
        query = db.query(PromptVersion).filter(PromptVersion.prompt_code == code)
        if version is not None:
            query = query.filter(PromptVersion.version == version)
        else:
            query = query.filter(PromptVersion.status == "ACTIVE") \
                .order_by(PromptVersion.version.desc())
        row = query.first()
        return row if row is not None else default_prompt()
    except Exception as exc:
        logger.warning("读取 Prompt 失败，使用内置默认值：%s", exc)
        return default_prompt()
