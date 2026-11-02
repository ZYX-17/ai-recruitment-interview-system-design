"""AI 任务 API：提交、轮询、重试；简历抽取同步入口。

任务状态机：PENDING → RUNNING → SUCCESS / FAILED →（重试）DEAD。
开发期用 FastAPI BackgroundTasks 异步执行；生产可替换为独立 worker（状态机不变）。
"""
import logging
from datetime import datetime, timezone

from fastapi import APIRouter, BackgroundTasks, Depends, Header, HTTPException
from sqlalchemy.orm import Session

from app.config import settings
from app.db import SessionLocal, get_db
from app.ids import new_id, new_task_no, new_trace_id
from app.models import AiResult, AiTask
from app.prompts.resume_extract import PROMPT_CODE
from app.schemas import ExtractResumeRequest, TaskSubmitRequest
from app.security import require_internal_token
from app.services.llm_router import llm_router
from app.services.resume_extraction import ExtractionError, ResumeExtractionService

router = APIRouter(prefix="/api/ai/v1", tags=["ai-tasks"])
logger = logging.getLogger(__name__)
extraction_service = ResumeExtractionService(llm_router)

SUPPORTED_BIZ_TYPES = {"RESUME_EXTRACT"}


def ok(data):
    return {"code": "0", "message": "成功", "data": data}


def utcnow() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def task_to_dict(task: AiTask) -> dict:
    return {
        "taskNo": task.task_no,
        "bizType": task.biz_type,
        "bizId": task.biz_id,
        "caller": task.caller,
        "status": task.status,
        "progress": task.progress,
        "model": task.model,
        "promptVersion": task.prompt_version,
        "retryCount": task.retry_count,
        "errorMsg": task.error_msg,
        "output": task.output_json,
        "createdAt": task.created_at.isoformat() if task.created_at else None,
        "finishedAt": task.finished_at.isoformat() if task.finished_at else None,
    }


@router.post("/tasks")
def submit_task(
        request: TaskSubmitRequest,
        background: BackgroundTasks,
        db: Session = Depends(get_db),
        trace_id: str | None = Header(default=None, alias="X-Trace-Id"),
        _=Depends(require_internal_token),
):
    """提交 AI 任务。RESUME_EXTRACT 的 input 需包含 text 或 markdown。"""
    biz_type = request.bizType.strip().upper()
    if biz_type not in SUPPORTED_BIZ_TYPES:
        raise HTTPException(status_code=400, detail=f"暂不支持 bizType={biz_type}")
    if not request.input_data:
        raise HTTPException(status_code=400, detail="input 不能为空")

    task = AiTask(
        id=new_id(),
        task_no=new_task_no(),
        biz_type=biz_type,
        biz_id=request.bizId,
        caller=request.caller,
        status="PENDING",
        progress=0,
        input_json=request.input_data,
        prompt_version=request.promptVersion,
        retry_count=0,
    )
    db.add(task)
    db.commit()
    db.refresh(task)

    trace = trace_id or new_trace_id()
    if request.asyncMode:
        background.add_task(run_task, task.task_no, trace)
    else:
        run_task(task.task_no, trace)
        db.expire_all()
        task = db.query(AiTask).filter(AiTask.task_no == task.task_no).first()
    return ok(task_to_dict(task))


@router.get("/tasks/{task_no}")
def get_task(
        task_no: str,
        db: Session = Depends(get_db),
        _=Depends(require_internal_token),
):
    task = db.query(AiTask).filter(AiTask.task_no == task_no).first()
    if task is None:
        raise HTTPException(status_code=404, detail="AI 任务不存在")
    return ok(task_to_dict(task))


@router.post("/tasks/{task_no}/retry")
def retry_task(
        task_no: str,
        background: BackgroundTasks,
        db: Session = Depends(get_db),
        trace_id: str | None = Header(default=None, alias="X-Trace-Id"),
        _=Depends(require_internal_token),
):
    task = db.query(AiTask).filter(AiTask.task_no == task_no).first()
    if task is None:
        raise HTTPException(status_code=404, detail="AI 任务不存在")
    if task.status == "SUCCESS":
        return ok(task_to_dict(task))
    if (task.retry_count or 0) >= settings.ai_task_max_retry:
        raise HTTPException(status_code=429, detail="重试次数超限")

    task.status = "PENDING"
    task.progress = 0
    task.error_msg = None
    db.commit()
    background.add_task(run_task, task.task_no, trace_id or new_trace_id())
    return ok(task_to_dict(task))


@router.post("/extract/resume")
def extract_resume(
        request: ExtractResumeRequest,
        db: Session = Depends(get_db),
        trace_id: str | None = Header(default=None, alias="X-Trace-Id"),
        _=Depends(require_internal_token),
):
    """同步抽取（resume-service 索引阶段调用）；结果不落 ai_task，只落调用日志。"""
    if not (request.text or "").strip():
        raise HTTPException(status_code=400, detail="text 不能为空")
    trace = trace_id or new_trace_id()
    try:
        outcome = extraction_service.extract(
            db, request.text, trace, prompt_version=request.promptVersion)
    except ExtractionError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    return ok({
        "structured": outcome.structured,
        "provider": outcome.provider,
        "model": outcome.model,
        "promptVersion": outcome.prompt_version,
        "latencyMs": outcome.latency_ms,
        "repaired": outcome.repaired,
    })


def run_task(task_no: str, trace_id: str | None = None) -> None:
    """后台执行任务；使用独立 Session，异常落 FAILED/DEAD。"""
    db = SessionLocal()
    trace = trace_id or new_trace_id()
    try:
        task = db.query(AiTask).filter(AiTask.task_no == task_no).first()
        if task is None or task.status == "SUCCESS":
            return
        task.status = "RUNNING"
        task.started_at = utcnow()
        task.progress = 10
        db.commit()

        if task.biz_type != "RESUME_EXTRACT":
            raise RuntimeError(f"暂不支持 bizType={task.biz_type}")
        input_json = task.input_json or {}
        text = str(input_json.get("text") or input_json.get("markdown") or "")
        if not text.strip():
            raise ValueError("input.text 不能为空")

        outcome = extraction_service.extract(
            db, text, trace, task_no=task.task_no, prompt_version=task.prompt_version)

        task = db.query(AiTask).filter(AiTask.task_no == task_no).first()
        task.status = "SUCCESS"
        task.progress = 100
        task.output_json = outcome.structured
        task.model = outcome.model
        task.prompt_code = PROMPT_CODE
        task.prompt_version = outcome.prompt_version
        task.error_msg = None
        task.finished_at = utcnow()

        result = db.query(AiResult).filter(AiResult.task_id == task.id).first()
        if result is None:
            result = AiResult(id=new_id(), task_id=task.id)
            db.add(result)
        result.result_json = outcome.structured
        db.commit()
        logger.info("AI 任务完成 taskNo=%s provider=%s model=%s",
                    task_no, outcome.provider, outcome.model)
    except Exception as exc:
        logger.exception("AI 任务失败 taskNo=%s", task_no)
        db.rollback()
        task = db.query(AiTask).filter(AiTask.task_no == task_no).first()
        if task is not None:
            retries = (task.retry_count or 0) + 1
            task.retry_count = retries
            task.status = "DEAD" if retries >= settings.ai_task_max_retry else "FAILED"
            task.error_msg = str(exc)[:512]
            task.finished_at = utcnow()
            db.commit()
    finally:
        db.close()
