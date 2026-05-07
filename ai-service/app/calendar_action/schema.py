"""챗봇 캘린더 액션 스키마"""
from typing import Optional, List, Dict, Any
from pydantic import BaseModel, Field


class CalendarActionRequest(BaseModel):
    """챗봇 캘린더 액션 요청"""
    question: Optional[str] = ""
    conversation_history: Optional[str] = ""
    session_id: Optional[str] = None
    action: Optional[str] = None


class CalendarActionButton(BaseModel):
    """사용자에게 보여줄 버튼"""
    label: str
    value: str


class CalendarActionState(BaseModel):
    """Redis에 저장되는 캘린더 액션 세션 상태"""
    session_id: str
    company_id: str
    member_id: str
    member_position_id: str
    slots: Dict[str, Any] = Field(default_factory=dict)


class CalendarActionResponse(BaseModel):
    """챗봇 캘린더 액션 응답"""
    type: str  # ask | confirm | created | cancelled | error
    message: str
    session_id: Optional[str] = None
    preview: Optional[Dict[str, Any]] = None
    actions: Optional[List[CalendarActionButton]] = None
    event_id: Optional[str] = None
    redirect_url: Optional[str] = None
    error_code: Optional[str] = None