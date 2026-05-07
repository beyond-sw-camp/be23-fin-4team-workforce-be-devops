"""approval-service API 호출 클라이언트"""
import httpx
import logging

logger = logging.getLogger(__name__)

from app.config import settings

GATEWAY_URL = settings.gateway_url


async def get_document_by_id(company_id: str, document_id: str) -> dict | None:
    """결재 양식 단건 조회 (internal)"""
    url = f"{GATEWAY_URL}/approval/documents/internal/{document_id}"
    headers = {
        "X-User-CompanyId": company_id
    }

    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(url, headers=headers)
            response.raise_for_status()
            data = response.json()

            if data.get("success"):
                logger.info(f"[approval_client] 양식 조회 성공: {document_id}")
                return data.get("data")
            else:
                logger.warning(f"[approval_client] 양식 조회 실패: {data}")
                return None

    except httpx.HTTPError as e:
        logger.error(f"[approval_client] 양식 조회 에러: {e}")
        return None


async def get_candidates(company_id: str, document_id: str, member_position_id: str, authorization: str) -> list[dict]:
    """양식별 결재자 후보 조회"""
    url = f"{GATEWAY_URL}/approval/policyLines/{document_id}/candidates"
    headers = {
        "X-User-CompanyId": company_id,
        "X-User-MemberPositionId": member_position_id,
        "Authorization": authorization
    }

    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(url, headers=headers)
            response.raise_for_status()
            data = response.json()

            if data.get("success"):
                candidates = data.get("data", [])
                logger.info(f"[approval_client] 결재자 후보 조회 성공: {len(candidates)}개")
                return candidates
            return []

    except httpx.HTTPError as e:
        logger.error(f"[approval_client] 결재자 후보 조회 에러: {e}")
        return []


async def create_request(
        company_id: str,
        member_id: str,
        member_position_id: str,
        payload: dict,
        authorization: str) -> dict | None:
    """결재 요청 생성"""
    url = f"{GATEWAY_URL}/approval/requests"
    headers = {
        "X-User-CompanyId": company_id,
        "X-User-UUID": member_id,
        "X-User-MemberPositionId": member_position_id,
        "Content-Type": "application/json",
        "Authorization": authorization
    }

    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            response = await client.post(url, headers=headers, json=payload)
            response.raise_for_status()
            data = response.json()

            if data.get("success"):
                logger.info(f"[approval_client] 결재 생성 성공: {data.get('data', {}).get('requestId')}")
                return data.get("data")
            else:
                logger.warning(f"[approval_client] 결재 생성 실패: {data}")
                return None

    except httpx.HTTPError as e:
        logger.error(f"[approval_client] 결재 생성 에러: {e}, body={payload}")
        return None