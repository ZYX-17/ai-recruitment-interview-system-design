"""内部接口鉴权：X-Internal-Token（与 Java 服务共用 INTERNAL_TOKEN）。"""
from fastapi import Header, HTTPException

from app.config import settings


async def require_internal_token(
        x_internal_token: str | None = Header(default=None, alias="X-Internal-Token")):
    if settings.internal_token and x_internal_token != settings.internal_token:
        raise HTTPException(status_code=403, detail="内部接口禁止外部访问")
