"""챗봇 액션 엔드포인트"""
from fastapi import APIRouter, Header
from app.action import service
from app.action.schema import ActionRequest, ActionResponse

router = APIRouter(prefix="/action", tags=["action"])


@router.post("", response_model=ActionResponse)
async def action(
        request: ActionRequest,
        x_user_company_id: str = Header(..., alias="X-User-CompanyId"),
        x_user_uuid: str = Header(..., alias="X-User-UUID"),
        x_user_member_position_id: str = Header(..., alias="X-User-MemberPositionId"),
        authorization: str = Header(..., alias="Authorization")):
    return await service.handle_action(
        request,
        x_user_company_id,
        x_user_uuid,
        x_user_member_position_id,
        authorization
    )