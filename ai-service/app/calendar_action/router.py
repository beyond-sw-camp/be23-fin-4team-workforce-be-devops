"""챗봇 캘린더 액션 엔드포인트"""
from fastapi import APIRouter, Header
from app.calendar_action import service
from app.calendar_action.schema import CalendarActionRequest, CalendarActionResponse

router = APIRouter(prefix="/calendar-action", tags=["calendar-action"])


@router.post("", response_model=CalendarActionResponse)
async def calendar_action(
        request: CalendarActionRequest,
        x_user_company_id: str = Header(..., alias="X-User-CompanyId"),
        x_user_uuid: str = Header(..., alias="X-User-UUID"),
        x_user_member_position_id: str = Header(..., alias="X-User-MemberPositionId"),
        authorization: str = Header(..., alias="Authorization")):
    return await service.handle_calendar_action(
        request,
        x_user_company_id,
        x_user_uuid,
        x_user_member_position_id,
        authorization
    )