"""챗봇 캘린더 액션 메인 로직"""
import json
import logging
from typing import Optional
from datetime import date, datetime, timedelta

from app.calendar_action import calendar_client, state
from app.calendar_action.schema import (
    CalendarActionRequest,
    CalendarActionResponse,
    CalendarActionState,
    CalendarActionButton
)
from app.core.openai import client as openai_client

logger = logging.getLogger(__name__)


# ============================================
# Function Calling Tool 정의 (고정)
# ============================================

CALENDAR_TOOL = {
    "type": "function",
    "function": {
        "name": "create_calendar_event",
        "description": "사용자가 요청한 캘린더 일정 정보를 추출합니다.",
        "parameters": {
            "type": "object",
            "properties": {
                "title": {
                    "type": "string",
                    "description": "일정 제목"
                },
                "description": {
                    "type": "string",
                    "description": "일정 상세 설명 (선택)"
                },
                "startAt": {
                    "type": "string",
                    "description": "시작일시 (YYYY-MM-DDTHH:mm:ss 형식)"
                },
                "endAt": {
                    "type": "string",
                    "description": "종료일시 (YYYY-MM-DDTHH:mm:ss 형식)"
                }
            }
        }
    }
}

REQUIRED_FIELDS = [
    {"name": "title", "label": "일정 제목"},
    {"name": "startAt", "label": "시작일시"},
    {"name": "endAt", "label": "종료일시"}
]


# ============================================
# 진입점
# ============================================

async def handle_calendar_action(
        request: CalendarActionRequest,
        company_id: str,
        member_id: str,
        member_position_id: str,
        authorization: str) -> CalendarActionResponse:
    """캘린더 액션 메인 핸들러"""
    logger.info(f"[calendar_action] ========================================")
    logger.info(
        f"[calendar_action] question='{request.question}', "
        f"session_id={request.session_id}, action={request.action}"
    )
    # 0. 액션 진행 중 사용자가 텍스트로 취소 의도 입력
    if request.session_id and request.question and not request.action:
        if _is_cancel_intent(request.question):
            logger.info(f"[calendar_action] 취소 키워드 감지로 세션 종료")
            state.clear_state(request.session_id)
            return CalendarActionResponse(
                type="cancelled",
                message="일정 등록을 취소했습니다. 새로운 질문을 입력해주세요.",
                session_id=None
            )

    # 1. 사용자가 버튼 누른 경우
    if request.action and request.session_id:
        return await _handle_user_action(
            request, company_id, member_id, authorization
        )

    # 2. 신규 또는 진행 중 세션
    current_state = _load_or_create_state(
        request.session_id, company_id, member_id, member_position_id
    )

    # 3. 슬롯 추출
    extracted = await _extract_slots(
        question=request.question,
        history=request.conversation_history,
        existing_slots=current_state.slots
    )
    if extracted:
        # 이미 채워진 슬롯은 보존 (LLM이 generic 값으로 덮어쓰는 것 방지)
        for key, value in extracted.items():
            if not value:
                continue
            existing = current_state.slots.get(key)
            # title은 이미 있으면 그대로 유지 (LLM이 "일정" 같은 generic으로 덮어씀 방지)
            if key == "title" and existing:
                continue
            current_state.slots[key] = value

    # 4. 부족한 필드 확인
    missing = _find_missing_fields(current_state.slots)

    if missing:
        state.save_state(current_state)
        return _ask_response(current_state, missing)

    # 5. 모든 슬롯 채워짐 → 확인 단계
    state.save_state(current_state)
    return _confirm_response(current_state)


# ============================================
# 사용자 액션 처리
# ============================================

async def _handle_user_action(
        request: CalendarActionRequest,
        company_id: str,
        member_id: str,
        authorization: str) -> CalendarActionResponse:
    """버튼 클릭 처리"""
    current_state = state.load_state(request.session_id)

    if not current_state:
        return _error_response("세션이 만료되었습니다. 다시 시작해주세요.", None)

    # 보안 검증
    if current_state.member_id != member_id:
        logger.warning(
            f"[calendar_action] 세션 도용 시도: "
            f"owner={current_state.member_id}, requester={member_id}"
        )
        return _error_response("세션에 접근할 권한이 없습니다.", None)

    action = request.action

    if action == "create_event":
        return await _create_event(current_state, authorization)

    if action == "cancel":
        state.clear_state(request.session_id)
        return CalendarActionResponse(
            type="cancelled",
            message="일정 등록이 취소되었습니다.",
            session_id=request.session_id
        )

    return _error_response(f"알 수 없는 액션: {action}", request.session_id)


# ============================================
# 일정 생성
# ============================================

async def _create_event(
        current_state: CalendarActionState,
        authorization: str) -> CalendarActionResponse:
    """개인 일정 즉시 생성"""
    payload = {
        "title": current_state.slots.get("title"),
        "description": current_state.slots.get("description", ""),
        "startAt": current_state.slots.get("startAt"),
        "endAt": current_state.slots.get("endAt"),
        "eventType": "PERSONAL",
        "isPublicYn": "YES"
    }

    result = await calendar_client.create_personal_event(
        current_state.company_id,
        current_state.member_id,
        payload,
        authorization
    )

    if not result:
        return _error_response(
            "일정 등록에 실패했습니다. 다시 시도해주세요.",
            current_state.session_id
        )

    state.clear_state(current_state.session_id)

    title = current_state.slots.get("title")
    start_at = current_state.slots.get("startAt", "")
    end_at = current_state.slots.get("endAt", "")

    formatted_start = _format_datetime(start_at)
    formatted_end = _format_datetime(end_at)

    message = (
        f"일정이 등록되었습니다.\n\n"
        f"- 제목: {title}\n"
        f"- 일시: {formatted_start} ~ {formatted_end}"
    )

    return CalendarActionResponse(
        type="created",
        message=message,
        event_id=result.get("eventId"),
        redirect_url="/app/calendar"
    )


# ============================================
# 슬롯 추출
# ============================================

async def _extract_slots(
        question: str,
        history: str,
        existing_slots: dict) -> dict:
    """LLM Function Calling으로 일정 정보 추출"""
    today = date.today()
    tomorrow = today + timedelta(days=1)
    day_after = today + timedelta(days=2)

    system_prompt = f"""당신은 캘린더 일정 작성 어시스턴트입니다.

[현재 날짜 정보]
- 오늘: {today.isoformat()} ({_get_weekday_korean(today)})
- 내일: {tomorrow.isoformat()}
- 모레: {day_after.isoformat()}
- 현재 시각 기준: 시간 미명시 시 09:00 등 일반 업무시간 사용 가능

[이미 입력된 정보]
{json.dumps(existing_slots, ensure_ascii=False) if existing_slots else "(없음)"}

[절대 규칙]
- 사용자 입력에 명시된 정보만 추출하세요. 추측 금지.
- title은 자연스럽게 자동 생성 가능 (예: "회의" → "회의")
- 날짜는 [현재 날짜 정보] 기준으로 변환
- 시간이 명시되지 않으면 startAt/endAt을 비워두세요. (예: "내일 회의" → 시간 없으니 미입력)
- 종료시간이 명시되지 않으면 시작시간 + 1시간으로 자동 설정 가능
- "오후 2시" → 14:00, "오전 10시" → 10:00
- 출력 형식: YYYY-MM-DDTHH:mm:ss (예: 2026-05-04T14:00:00)

[자동 생성 허용]
- title: 일정 내용에 어울리는 제목 자동 생성 (단, [이미 입력된 정보]에 title이 있으면 그대로 유지하고 절대 변경하지 마세요)
- endAt: startAt 있고 endAt 없으면 startAt + 1시간

[이전 슬롯 보존 규칙]
[이미 입력된 정보]에 어떤 필드가 있으면 그 필드는 그대로 두세요.
사용자가 명시적으로 변경을 요청하지 않았다면 새 값으로 덮어쓰지 마세요.

예시:
[이미 입력된 정보] {{ "title": "회의" }}
입력: "내일 오후 2시부터 3시까지"
올바름: {{ "title": "회의", "startAt": "...", "endAt": "..." }} (title 유지)
잘못: {{ "title": "일정", "startAt": "...", "endAt": "..." }} (title 변경 X)

[이미 입력된 정보] {{ "title": "주간회의" }}
입력: "다음주 수요일 12시부터 14시까지"
올바름: {{ "title": "주간회의", "startAt": "...", "endAt": "..." }} (title 유지)

[잘못된 추출 예시]
입력: "회의 잡아줘"
잘못: {{ "title": "회의", "startAt": "2026-05-03T09:00:00", "endAt": "2026-05-03T10:00:00" }}
올바름: {{ "title": "회의" }} (날짜 없으니 시간 추출 안 함)

입력: "내일 회의"
잘못: {{ "title": "회의", "startAt": "2026-05-04T09:00:00" }}
올바름: {{ "title": "회의" }} (시간이 명시 안 됨)

입력: "내일 오후 2시 회의"
올바름: {{ "title": "회의", "startAt": "2026-05-04T14:00:00", "endAt": "2026-05-04T15:00:00" }}
"""

    messages = [{"role": "system", "content": system_prompt}]
    if history:
        messages.append({"role": "user", "content": f"[이전 대화]\n{history}"})
    messages.append({"role": "user", "content": f"[현재 입력]\n{question}"})

    try:
        response = openai_client.chat.completions.create(
            model="gpt-4o-mini",
            messages=messages,
            tools=[CALENDAR_TOOL],
            tool_choice="auto",
            temperature=0.0
        )

        message = response.choices[0].message
        if not message.tool_calls:
            return {}

        tool_call = message.tool_calls[0]
        arguments = json.loads(tool_call.function.arguments)
        cleaned = {k: v for k, v in arguments.items() if v is not None and v != ""}
        logger.info(f"[calendar_action] 추출된 슬롯: {cleaned}")
        return cleaned

    except Exception as e:
        logger.error(f"[calendar_action] 슬롯 추출 실패: {e}")
        return {}


# ============================================
# 헬퍼 함수
# ============================================

def _find_missing_fields(slots: dict) -> list:
    """필수 필드 중 빠진 것 반환"""
    missing = []
    for field in REQUIRED_FIELDS:
        value = slots.get(field["name"])
        if value is None or value == "":
            missing.append(field)
    return missing


def _load_or_create_state(
        session_id: Optional[str],
        company_id: str,
        member_id: str,
        member_position_id: str) -> CalendarActionState:
    """기존 세션 로드 or 신규 생성"""
    if session_id:
        existing = state.load_state(session_id)
        if existing:
            if existing.member_id != member_id:
                logger.warning(
                    f"[calendar_action] 세션 도용 시도: "
                    f"owner={existing.member_id}, requester={member_id}"
                )
                new_session_id = state.create_session_id()
                return CalendarActionState(
                    session_id=new_session_id,
                    company_id=company_id,
                    member_id=member_id,
                    member_position_id=member_position_id,
                    slots={}
                )
            return existing

    new_session_id = state.create_session_id()
    return CalendarActionState(
        session_id=new_session_id,
        company_id=company_id,
        member_id=member_id,
        member_position_id=member_position_id,
        slots={}
    )


def _ask_response(
        current_state: CalendarActionState,
        missing: list) -> CalendarActionResponse:
    """추가 정보 요청"""
    if len(missing) == 1:
        message = f"{missing[0]['label']}을(를) 알려주세요."
    else:
        labels = [m["label"] for m in missing]
        message = f"다음 정보를 알려주세요:\n- " + "\n- ".join(labels)

    message += "\n\n(다른 질문을 하시려면 \"취소\"라고 입력해주세요)"

    return CalendarActionResponse(
        type="ask",
        message=message,
        session_id=current_state.session_id
    )


def _confirm_response(
        current_state: CalendarActionState) -> CalendarActionResponse:
    """등록 확인"""
    title = current_state.slots.get("title", "")
    start_at = current_state.slots.get("startAt", "")
    end_at = current_state.slots.get("endAt", "")
    description = current_state.slots.get("description", "")

    formatted_start = _format_datetime(start_at)
    formatted_end = _format_datetime(end_at)

    preview_lines = [
        f"- 제목: {title}",
        f"- 시작일시: {formatted_start}",
        f"- 종료일시: {formatted_end}"
    ]
    if description:
        preview_lines.append(f"- 설명: {description}")

    message = (
            f"다음 내용으로 일정을 등록합니다.\n\n"
            + "\n".join(preview_lines)
            + "\n\n등록하시겠습니까?"
    )

    return CalendarActionResponse(
        type="confirm",
        message=message,
        session_id=current_state.session_id,
        preview={
            "title": title,
            "startAt": start_at,
            "endAt": end_at,
            "description": description
        },
        actions=[
            CalendarActionButton(label="등록", value="create_event"),
            CalendarActionButton(label="취소", value="cancel")
        ]
    )


def _error_response(
        message: str,
        session_id: Optional[str]) -> CalendarActionResponse:
    return CalendarActionResponse(
        type="error",
        message=message,
        session_id=session_id,
        error_code="GENERIC_ERROR"
    )


def _format_datetime(iso_str: str) -> str:
    """ISO 형식을 사람이 읽기 좋게 변환"""
    if not iso_str:
        return ""
    try:
        dt = datetime.fromisoformat(iso_str)
        weekday = _get_weekday_korean(dt.date())
        return f"{dt.strftime('%Y-%m-%d')} ({weekday}) {dt.strftime('%H:%M')}"
    except Exception:
        return iso_str


def _get_weekday_korean(target_date: date) -> str:
    weekdays = ["월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"]
    return weekdays[target_date.weekday()]


def _is_cancel_intent(question: str) -> bool:
    """사용자 입력이 취소/중단 의도인지 감지"""
    cancel_keywords = [
        "취소", "그만", "안할래", "안할게", "관둬", "중단",
        "됐어", "필요없어", "그만할래", "스톱", "stop",
        "다른 질문", "다른거", "다른 거"
    ]
    question_lower = question.strip().lower()
    return any(kw in question_lower for kw in cancel_keywords)