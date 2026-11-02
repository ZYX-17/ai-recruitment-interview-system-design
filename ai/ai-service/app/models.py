"""recruit_ai 核心表模型（与 sql/07_recruit_ai.sql 对齐）。"""
from sqlalchemy import (
    BigInteger,
    Column,
    DateTime,
    Integer,
    JSON,
    Numeric,
    SmallInteger,
    String,
    Text,
    UniqueConstraint,
    func,
)

from app.db import Base


class AiTask(Base):
    __tablename__ = "ai_task"

    id = Column(BigInteger, primary_key=True)
    task_no = Column(String(64), unique=True, nullable=False, index=True)
    biz_type = Column(String(32), nullable=False)
    biz_id = Column(String(64), nullable=False)
    caller = Column(String(32), nullable=False)
    status = Column(String(16), nullable=False, default="PENDING")
    progress = Column(SmallInteger, nullable=False, default=0)
    input_json = Column(JSON, nullable=False)
    output_json = Column(JSON)
    model = Column(String(64))
    prompt_code = Column(String(64))
    prompt_version = Column(Integer)
    retry_count = Column(Integer, nullable=False, default=0)
    error_msg = Column(String(512))
    started_at = Column(DateTime)
    finished_at = Column(DateTime)
    created_at = Column(DateTime, nullable=False, server_default=func.now())
    updated_at = Column(DateTime, nullable=False, server_default=func.now(), onupdate=func.now())
    version = Column(Integer, nullable=False, default=0)


class PromptVersion(Base):
    __tablename__ = "prompt_version"
    __table_args__ = (
        UniqueConstraint("prompt_code", "version", name="uk_code_ver"),
    )

    id = Column(BigInteger, primary_key=True)
    prompt_code = Column(String(64), nullable=False)
    version = Column(Integer, nullable=False)
    content = Column(Text, nullable=False)
    response_schema = Column(JSON)
    model_hint = Column(String(64))
    status = Column(String(16), nullable=False, default="DRAFT")
    author_id = Column(BigInteger)
    created_at = Column(DateTime, nullable=False, server_default=func.now())
    updated_at = Column(DateTime, nullable=False, server_default=func.now(), onupdate=func.now())


class AiResult(Base):
    __tablename__ = "ai_result"

    id = Column(BigInteger, primary_key=True)
    task_id = Column(BigInteger, nullable=False, unique=True, index=True)
    result_json = Column(JSON, nullable=False)
    confidence = Column(Numeric(4, 3))
    evidence = Column(JSON)
    created_at = Column(DateTime, nullable=False, server_default=func.now())


class AiCallLog(Base):
    __tablename__ = "ai_call_log"

    id = Column(BigInteger, primary_key=True)
    trace_id = Column(String(64), nullable=False, index=True)
    task_no = Column(String(64), index=True)
    capability = Column(String(16), nullable=False)
    provider_name = Column(String(32), nullable=False)
    model = Column(String(64), nullable=False)
    prompt_code = Column(String(64))
    prompt_version = Column(Integer)
    input_tokens = Column(Integer)
    output_tokens = Column(Integer)
    latency_ms = Column(Integer, nullable=False)
    status = Column(String(16), nullable=False)
    degraded_to = Column(String(64))
    error_msg = Column(String(512))
    created_at = Column(DateTime, nullable=False, server_default=func.now())


class HumanReview(Base):
    __tablename__ = "human_review"

    id = Column(BigInteger, primary_key=True)
    biz_type = Column(String(32), nullable=False)
    biz_id = Column(String(64), nullable=False)
    task_no = Column(String(64))
    reviewer_id = Column(BigInteger, nullable=False)
    status = Column(String(16), nullable=False, default="PENDING")
    comment = Column(String(1024))
    reviewed_at = Column(DateTime)
    created_at = Column(DateTime, nullable=False, server_default=func.now())
    updated_at = Column(DateTime, nullable=False, server_default=func.now(), onupdate=func.now())
