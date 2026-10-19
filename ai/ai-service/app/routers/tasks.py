"""AI 任务路由（占位骨架，OpenAPI 契约见 docs/3.开发文档 6.2）。

业务方（resume/interview/room）经 Feign → /api/ai/v1/tasks 提交任务；
任务状态机 PENDING/RUNNING/SUCCESS/FAILED/DEAD；产物写 ai_result + human_review。
"""
from fastapi import APIRouter

router = APIRouter(prefix="/api/ai/v1", tags=["ai-tasks"])


@router.post("/tasks")
async def submit_task(task: dict):
    """提交 AI 任务（biz_type: RESUME_EXTRACT/MATCH/MINUTES/ANSWER_SCORE/PROFILE/TAG）。"""
    # TODO(W3~4)：入库 ai_task → 路由到对应能力 → 异步执行 → ai_task.completed 事件
    return {"code": "0", "message": "accepted(skeleton)", "data": {"task_no": "TODO", **task}}


@router.get("/tasks/{task_no}")
async def get_task(task_no: str):
    """轮询任务状态（前端解析进度轮询入口）。"""
    return {"code": "0", "data": {"task_no": task_no, "status": "PENDING(skeleton)"}}
