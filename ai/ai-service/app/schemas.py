"""Pydantic 契约：任务提交 + 简历结构化抽取结果。"""
from pydantic import BaseModel, ConfigDict, Field


class SkillItem(BaseModel):
    name: str
    level: str | None = None
    years: float | None = None


class ExperienceItem(BaseModel):
    company: str | None = None
    title: str | None = None
    startDate: str | None = None
    endDate: str | None = None
    description: str | None = None


class EducationItem(BaseModel):
    school: str | None = None
    major: str | None = None
    degree: str | None = None
    startDate: str | None = None
    endDate: str | None = None


class ProjectItem(BaseModel):
    name: str | None = None
    role: str | None = None
    description: str | None = None


class ResumeExtraction(BaseModel):
    """简历结构化结果；AI 输出为草稿，业务方人工确认后才算正式数据。"""

    name: str | None = None
    phone: str | None = None
    email: str | None = None
    city: str | None = None
    education: str | None = None
    workYears: int | None = None
    skills: list[SkillItem] = Field(default_factory=list)
    experiences: list[ExperienceItem] = Field(default_factory=list)
    educations: list[EducationItem] = Field(default_factory=list)
    projects: list[ProjectItem] = Field(default_factory=list)
    certificates: list[str] = Field(default_factory=list)
    summary: str | None = None


class TaskSubmitRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    bizType: str
    bizId: str
    caller: str = "unknown"
    input_data: dict = Field(default_factory=dict, alias="input")
    asyncMode: bool = True
    promptVersion: int | None = None


class ExtractResumeRequest(BaseModel):
    resumeId: str | None = None
    text: str
    promptVersion: int | None = None
