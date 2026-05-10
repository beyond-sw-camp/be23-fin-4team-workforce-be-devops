from app.core.openai import get_embedding, expand_query, detect_category
from app.core.pinecone import search_vectors
from datetime import date
import asyncio
import logging

logger = logging.getLogger(__name__)

NO_CONTEXT_MARKER = "__NO_RELEVANT_DOCUMENT__"


# Layer 우선순위 (높을수록 우선)
LAYER_PRIORITY = {
    "db_sync": 3,        # DB 최신 정책 (최우선)
    "hr_uploaded": 2,    # HR 업로드 문서
    "platform": 1,       # 플랫폼 공통 (최후)
}

# Layer별 점수 보너스
# 정책 질문은 detect_category가 카테고리를 반환해 include_platform=False가 되며
# platform/hr_uploaded가 검색에서 빠진다 → db_sync 답변 영향 0.
# 액션 가이드 질문(어디서/어떻게)만 '기타'로 분류돼 전체 검색되며 boost 작동.
LAYER_BOOST = {
    "db_sync": 0.0,
    "hr_uploaded": 0.20,    # 회사 자체 업로드 문서 우위
    "platform": 0.20,       # 액션 가이드 질문에서 platform 우위 확보
}


def apply_layer_priority(matches, final_top_k=5):
    """
    Layer 우선순위 적용:
    1. 같은 (category, subcategory)에 여러 layer가 매칭되면 우선순위 높은 것만 선택
    2. 조정 점수(원래 점수 + layer 보너스)로 재정렬
    3. top_k개 반환

    우선순위: db_sync > hr_uploaded > platform
    """
    if not matches:
        return matches

    # 1단계: 같은 subcategory 내에서는 우선순위 높은 layer만 선택
    seen = {}  # key: (category, subcategory), value: match

    for match in matches:
        metadata = match.metadata or {}
        category = metadata.get("category", "일반")
        subcategory = metadata.get("subcategory", "")
        layer = metadata.get("layer", "hr_uploaded")
        key = (category, subcategory)

        if key not in seen:
            seen[key] = match
        else:
            existing_layer = (seen[key].metadata or {}).get("layer", "hr_uploaded")
            existing_priority = LAYER_PRIORITY.get(existing_layer, 0)
            new_priority = LAYER_PRIORITY.get(layer, 0)

            # 새 매치의 layer 우선순위가 더 높으면 교체
            if new_priority > existing_priority:
                seen[key] = match

    # 2단계: 중복 제거된 결과에 layer 보너스 점수 적용
    deduplicated = []
    for match in seen.values():
        layer = (match.metadata or {}).get("layer", "hr_uploaded")
        boost = LAYER_BOOST.get(layer, 0.0)
        deduplicated.append({
            "match": match,
            "original_score": match.score,
            "adjusted_score": match.score + boost,
        })
    # 3단계: 조정된 점수로 재정렬
    deduplicated.sort(key=lambda m: m["adjusted_score"], reverse=True)

    # 4단계: top_k 반환
    result = deduplicated[:final_top_k]

    logger.info(
        f"[layer_priority] {len(matches)}건 "
        f"→ {len(deduplicated)}건 (중복 제거) "
        f"→ {len(result)}건 (top_k)"
    )
    for i, item in enumerate(result):
        match = item["match"]
        md = match.metadata or {}
        logger.info(
            f"  #{i+1} orig={item['original_score']:.3f} "
            f"adj={item['adjusted_score']:.3f} "
            f"layer={md.get('layer')} "
            f"[{md.get('category')}/{md.get('subcategory')}]"
        )
    return [item["match"] for item in result]


def _build_date_info() -> str:
    """현재 날짜 정보 헤더 생성 - LLM이 시점 표현 정확히 해석하도록"""
    today = date.today()
    weekdays = ["월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"]
    return f"""[현재 날짜 정보]
- 오늘: {today.isoformat()} ({weekdays[today.weekday()]})
- 이번달: {today.year}년 {today.month}월
- 올해: {today.year}년

위 날짜 정보를 활용해 "오늘", "내일", "이번달", "올해" 같은 시점 표현을 정확하게 해석하세요.

---

"""


async def rag_search(
        question: str,
        company_id: str,
        conversation_history: str = "") -> dict:
    logger.info(f"[rag_search] ========================================")
    logger.info(f"[rag_search] Q: '{question}'")

    if conversation_history:
        logger.info(f"[rag_search] 이전 대화 있음 ({len(conversation_history)}자)")

    if not question or not question.strip():
        return {"context": NO_CONTEXT_MARKER, "sources": []}

    category = await asyncio.to_thread(detect_category, question)
    expanded_question = await asyncio.to_thread(
        expand_query,
        question,
        conversation_history
    )
    query_vector = await asyncio.to_thread(get_embedding, expanded_question)

    matches = []
    if category:
        logger.info(f"[rag_search] 카테고리 필터 검색: {category} (가이드 제외)")
        raw_matches = await asyncio.to_thread(
            search_vectors,
            query_vector,
            company_id,
            top_k=10,
            min_score=0.30,
            category=category,
            include_platform=False  # 정책 정보 질문이므로 가이드 제외
        )

        # layer 우선순위 재정렬
        matches = apply_layer_priority(raw_matches, final_top_k=5)

    if not matches:
        if category:
            logger.info(
                f"[rag_search] 카테고리({category}) 매칭 없음, 전체 검색"
            )
        else:
            logger.info(f"[rag_search] 카테고리 미감지, 전체 검색")

        raw_matches = await asyncio.to_thread(
            search_vectors,
            query_vector,
            company_id,
            top_k=10,
            min_score=0.30,
            category=None,
            include_platform=True
        )

        # 2차 검색에도 layer 우선순위 적용
        matches = apply_layer_priority(raw_matches, final_top_k=5)

    logger.info(f"[rag_search] 최종 매칭 {len(matches)}개")
    for i, m in enumerate(matches):
        md = m.metadata
        content_preview = md.get('content', '')[:60].replace('\n', ' ')
        logger.info(
            f"  #{i+1} score={m.score:.3f} "
            f"layer={md.get('layer', '?')} "
            f"[{md.get('category', '?')}/{md.get('subcategory', '?')}] "
            f"'{content_preview}...'"
        )

    if not matches:
        return {"context": NO_CONTEXT_MARKER, "sources": []}

    seen_content = set()
    context_parts = []
    sources = set()

    for m in matches:
        md = m.metadata
        content = md.get('content', '').strip()
        document_name = md.get('document_name', '문서')

        content_hash = hash(content)
        if content_hash in seen_content:
            continue
        seen_content.add(content_hash)

        if len(content) > 1200:
            content = content[:1200]

        context_parts.append(content)
        sources.add(document_name)

    # 현재 날짜 정보 헤더 + 검색 결과 합쳐서 반환
    context = _build_date_info() + "\n\n---\n\n".join(context_parts)

    logger.info(
        f"[rag_search] context {len(context)}자, sources={list(sources)}"
    )

    return {
        "context": context,
        "sources": list(sources)
    }