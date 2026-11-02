"""ID / 编号生成（BIGINT 主键 + 可读 task_no）。"""
import uuid
from datetime import datetime, timezone


def new_id() -> int:
    """63 位随机正整数，避免与 Java 雪花 ID 冲突（不同库，实际不要求全局唯一）。"""
    return uuid.uuid4().int % (1 << 63)


def new_task_no() -> str:
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
    return f"AI{stamp}{uuid.uuid4().hex[:8].upper()}"


def new_trace_id() -> str:
    return uuid.uuid4().hex
