from __future__ import annotations
from pinecone import Pinecone
from app.config import settings
import logging

logger = logging.getLogger(__name__)

pc = Pinecone(api_key=settings.pinecone_api_key)
index = pc.Index(settings.pinecone_index_name)

# 플랫폼 공통 문서 저장 시 사용하는 특수 company_id
PLATFORM_COMPANY_ID = "__PLATFORM__"


def upsert_vectors(vectors: list[dict]):
    index.upsert(vectors=vectors)


def search_vectors(
        query_vector: list[float],
        company_id: str,
        top_k: int = 5,
        min_score: float = 0.35,
        category: str | None = None,
        include_platform: bool = True,
        document_name: str | None = None) -> list[dict]:
    """
    Pinecone 벡터 검색.

    Args:
        include_platform: True면 플랫폼 공통 문서도 함께 검색 (기본 True)
        document_name: 지정 시 해당 문서명의 청크만 검색 (라우팅 용도)
    """
    # 회사 ID 필터
    if include_platform:
        company_filter = {
            "company_id": {"$in": [company_id, PLATFORM_COMPANY_ID]}
        }
    else:
        company_filter = {"company_id": {"$eq": company_id}}

    filter_dict = company_filter.copy()
    if category:
        filter_dict["category"] = {"$eq": category}
    if document_name:
        filter_dict["document_name"] = {"$eq": document_name}

    logger.info(
        f"[search_vectors] top_k={top_k}, min_score={min_score}, "
        f"filter={filter_dict}"
    )

    results = index.query(
        vector=query_vector,
        top_k=top_k,
        filter=filter_dict,
        include_metadata=True
    )

    logger.info(f"[search_vectors] 전체 결과 {len(results.matches)}개:")
    for i, m in enumerate(results.matches):
        md = m.metadata
        logger.info(
            f"  #{i+1} score={m.score:.3f} "
            f"layer={md.get('layer', '?')} "
            f"[{md.get('category', '?')}/{md.get('subcategory', '?')}]"
        )

    filtered = [
        match for match in results.matches
        if match.score >= min_score
    ]

    logger.info(
        f"[search_vectors] 임계값({min_score}) 통과: {len(filtered)}개"
    )

    return filtered


def delete_vectors(document_id: str):
    index.delete(
        filter={"document_id": {"$eq": document_id}}
    )