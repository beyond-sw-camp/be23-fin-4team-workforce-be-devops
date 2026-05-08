"""member-service 캘린더 API 호출 클라이언트"""
import httpx
import logging

logger = logging.getLogger(__name__)

GATEWAY_URL = settings.gateway_url


async def create_personal_event(
        company_id: str,
        member_id: str,
        payload: dict,
        authorization: str) -> dict | None:
    """개인 일정 생성"""
    url = f"{GATEWAY_URL}/calendar/personal"
    headers = {
        "X-User-CompanyId": company_id,
        "X-User-UUID": member_id,
        "Content-Type": "application/json",
        "Authorization": authorization
    }

    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.post(url, headers=headers, json=payload)
            response.raise_for_status()
            data = response.json()

            if not data.get("success"):
                logger.warning(f"[calendar_client] 일정 생성 실패: {data}")
                return None

            # data.data가 string(UUID)이거나 dict일 수 있음
            inner = data.get("data")
            if isinstance(inner, str):
                event_id = inner
            elif isinstance(inner, dict):
                event_id = inner.get("eventId") or inner.get("id")
            else:
                event_id = None

            logger.info(f"[calendar_client] 일정 생성 성공: {event_id}")
            return {"eventId": event_id}

    except httpx.HTTPError as e:
        logger.error(f"[calendar_client] 일정 생성 에러: {e}, body={payload}")
        return None