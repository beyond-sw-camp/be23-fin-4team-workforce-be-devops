"""member-service API 호출 클라이언트"""
import httpx
import logging

logger = logging.getLogger(__name__)

from app.config import settings

GATEWAY_URL = settings.gateway_url


async def search_members(
        company_id: str,
        member_id: str,
        member_position_id: str,
        keyword: str,
        authorization: str) -> list[dict]:
    """이름/키워드로 멤버 검색 (같은 회사 내)"""
    url = f"{GATEWAY_URL}/member/search"
    headers = {
        "X-User-CompanyId": company_id,
        "X-User-UUID": member_id,
        "X-User-MemberPositionId": member_position_id,
        "Authorization": authorization
    }
    params = {
        "keyword": keyword,
        "memberStatus": "ACTIVE",
        "page": 0,
        "size": 10
    }

    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(url, headers=headers, params=params)
            response.raise_for_status()
            data = response.json()

            if data.get("success"):
                content = data.get("data", {}).get("content", [])
                logger.info(f"[member_client] 검색 결과 {len(content)}명: keyword='{keyword}'")
                return content
            return []

    except httpx.HTTPError as e:
        logger.error(f"[member_client] 멤버 검색 에러: {e}")
        return []