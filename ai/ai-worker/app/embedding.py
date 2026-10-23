"""BGE Embedding：mock 确定性向量 / 真实 bge-small-zh-v1.5。"""
import hashlib
import logging
import math
import os
import threading

logger = logging.getLogger(__name__)

EMBED_MODE = os.getenv("EMBED_MODE", "mock").strip().lower()
EMBED_DIMS = int(os.getenv("EMBED_DIMS", "512"))
EMBED_MODEL = os.getenv("EMBED_MODEL", "bge-small-zh-v1.5")
BGE_MODEL_PATH = os.getenv("BGE_MODEL_PATH", "BAAI/bge-small-zh-v1.5")

_model = None
_lock = threading.Lock()


def embed_texts(texts: list[str]) -> list[list[float]]:
    """返回与输入等长的向量列表；mock 与 bge 都输出 512 维。"""
    if EMBED_MODE == "bge":
        return _embed_bge(texts)
    return [_mock_vector(text) for text in texts]


def _mock_vector(text: str) -> list[float]:
    """确定性哈希向量：同一文本永远得到同一向量，便于单测与本地联调。"""
    values: list[float] = []
    counter = 0
    while len(values) < EMBED_DIMS:
        digest = hashlib.sha256(f"{counter}:{text}".encode("utf-8")).digest()
        for offset in range(0, len(digest), 4):
            if len(values) >= EMBED_DIMS:
                break
            integer = int.from_bytes(digest[offset:offset + 4], "big")
            values.append(integer / 2**31 - 1.0)
        counter += 1
    norm = math.sqrt(sum(value * value for value in values)) or 1.0
    return [value / norm for value in values]


def _embed_bge(texts: list[str]) -> list[list[float]]:
    global _model
    try:
        from sentence_transformers import SentenceTransformer
    except ImportError as exc:
        raise RuntimeError("EMBED_MODE=bge 但未安装 sentence-transformers") from exc

    with _lock:
        if _model is None:
            logger.info("加载 BGE 模型 %s", BGE_MODEL_PATH)
            _model = SentenceTransformer(BGE_MODEL_PATH)
        model = _model

    vectors = model.encode(texts, normalize_embeddings=True, batch_size=32)
    return [vector.tolist() for vector in vectors]
