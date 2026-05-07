"""챗봇 액션 스키마"""
from typing import Optional, List, Dict, Any
from pydantic import BaseModel, Field


class ActionRequest(BaseModel):
    """챗봇 액션 요청"""
    question: Optional[str] = ""
    conversation_history: Optional[str] = ""
    session_id: Optional[str] = None
    action: Optional[str] = None  # 사용자 버튼 액션


class ActionButton(BaseModel):
    """사용자에게 보여줄 버튼"""
    label: str
    value: str


class PreviewField(BaseModel):
    """미리보기 필드"""
    label: str
    value: Any


class ApprovalLineItem(BaseModel):
    """결재선 항목"""
    stepOrder: int
    approverMemberId: str
    approverMemberPositionId: str
    approverName: Optional[str] = None
    organizationName: Optional[str] = None


class PendingStep(BaseModel):
    """후보 여러 명인 미정 stepOrder"""
    stepOrder: int
    jobTitleId: Optional[str] = None
    jobTitleName: Optional[str] = None
    candidates: List[Dict[str, Any]]  # MemberPositionResDto 형식


class ActionState(BaseModel):
    """Redis에 저장되는 액션 세션 상태"""
    session_id: str
    company_id: str
    member_id: str
    member_position_id: str

    document_id: Optional[str] = None
    document_name: Optional[str] = None
    form_schema: Optional[str] = None
    request_type: Optional[str] = None

    slots: Dict[str, Any] = Field(default_factory=dict)

    # 결재선 관련
    approval_lines: List[ApprovalLineItem] = Field(default_factory=list)  # 확정된 결재선
    pending_steps: List[PendingStep] = Field(default_factory=list)  # 아직 선택 안 된 단계
    waiting_for_approver_name: bool = False  # 사용자가 결재자 이름 입력 대기 중인지
    pending_added_approvers: List[Dict[str, Any]] = Field(default_factory=list)  # 검색 결과가 여러 명일 때 임시 저장


class ActionResponse(BaseModel):
    """챗봇 액션 응답"""
    type: str  # ask | select_approver | ask_add_approver | confirm | submitted | draft_for_attachment | cancelled | error
    message: str
    session_id: Optional[str] = None
    preview: Optional[Dict[str, Any]] = None
    actions: Optional[List[ActionButton]] = None
    request_id: Optional[str] = None
    redirect_url: Optional[str] = None
    error_code: Optional[str] = None