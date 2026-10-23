"""文档解析适配：mock 轻量抽取 / MinerU CLI。"""
import logging
import shutil
import subprocess
from pathlib import Path

from app.config import Settings

logger = logging.getLogger(__name__)


def parse_document(path: Path, file_name: str, mime: str, settings: Settings) -> tuple[str, str]:
    """返回 (markdown, parser_version)。"""
    if settings.parser_mode == "mock":
        return _parse_mock(path, file_name, mime), "mock-1.0"
    return _parse_mineru(path, settings), "mineru-cli"


def _parse_mock(path: Path, file_name: str, mime: str) -> str:
    """无 GPU 的轻量抽取：PDF/DOCX/文本尽量取正文，图片给占位说明。"""
    text = ""
    suffix = path.suffix.lower()
    try:
        if suffix == ".pdf" or mime == "application/pdf":
            text = _extract_pdf(path)
        elif suffix == ".docx":
            text = _extract_docx(path)
        elif suffix in {".txt", ".md"} or mime in {"text/plain", "text/markdown"}:
            text = path.read_text(encoding="utf-8", errors="ignore")
        elif suffix in {".jpg", ".jpeg", ".png", ".webp"}:
            text = "（图片简历，mock 模式不做 OCR；切换 PARSER_MODE=mineru 后可识别）"
    except Exception as exc:  # 拉不到正文不应阻断链路
        logger.warning("mock 文本抽取失败，使用占位内容: %s", exc)
        text = ""

    if not text.strip():
        text = "（mock 模式未能抽取到文本，链路仍可继续）"
    return f"# {file_name}\n\n> 解析模式：mock\n> MIME：{mime}\n\n{text.strip()}\n"


def _extract_pdf(path: Path) -> str:
    from pypdf import PdfReader

    reader = PdfReader(str(path))
    return "\n\n".join((page.extract_text() or "") for page in reader.pages)


def _extract_docx(path: Path) -> str:
    from docx import Document

    document = Document(str(path))
    return "\n".join(p.text for p in document.paragraphs if p.text.strip())


def _parse_mineru(path: Path, settings: Settings) -> str:
    cli = shutil.which(settings.mineru_cli)
    if cli is None:
        raise RuntimeError(
            f"MinerU CLI 未安装（{settings.mineru_cli}）；本地请设置 PARSER_MODE=mock"
        )

    out_dir = path.parent / "mineru-out"
    out_dir.mkdir(parents=True, exist_ok=True)
    command = [cli, "-p", str(path), "-o", str(out_dir)]
    logger.info("调用 MinerU: %s", " ".join(command))
    result = subprocess.run(
        command,
        check=False,
        timeout=settings.mineru_timeout_seconds,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(f"MinerU 退出码 {result.returncode}: {result.stderr[-500:]}")

    markdown_files = sorted(out_dir.rglob("*.md"))
    if not markdown_files:
        raise RuntimeError("MinerU 未产出 Markdown")
    return markdown_files[0].read_text(encoding="utf-8", errors="ignore")
