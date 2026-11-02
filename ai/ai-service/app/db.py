"""SQLAlchemy 引擎/会话（recruit_ai）。

开发期默认 `AI_DB_AUTO_CREATE=true`，启动时 create_all + 种子 Prompt；
生产可关闭，改用 Alembic 迁移。
"""
import logging

from sqlalchemy import create_engine
from sqlalchemy.orm import declarative_base, sessionmaker

from app.config import settings

logger = logging.getLogger(__name__)

engine = create_engine(
    settings.database_url,
    pool_pre_ping=True,
    pool_recycle=1800,
    future=True,
)
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False, future=True)
Base = declarative_base()


def get_db():
    """FastAPI 依赖：每个请求一个 Session。"""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def init_db() -> None:
    """建表 + 写入默认 Prompt；数据库不可用时只告警，不阻塞服务启动。"""
    if not settings.ai_db_auto_create:
        return
    try:
        from app import models  # noqa: F401  确保模型已注册到 Base
        from app.services.prompt_service import ensure_default_prompts

        Base.metadata.create_all(bind=engine)
        with SessionLocal() as db:
            ensure_default_prompts(db)
        logger.info("recruit_ai 表结构就绪")
    except Exception as exc:  # 数据库未启动时健康检查会标记 degraded
        logger.warning("recruit_ai 初始化失败（服务继续启动）：%s", exc)
