"""챗봇 액션 메인 로직 (단순화 버전)
사용자 슬롯 추출 후 → 결재 작성 화면으로 prefill 데이터 전달
실제 결재 생성은 사용자가 화면에서 직접 수행
"""
import json
import logging
import re
from typing import Optional

from app.config import settings
from app.action import approval_client, form_builder, state
from app.action.schema import ActionRequest, ActionResponse, ActionState, ActionButton
from app.core.openai import client as openai_client
from app.core.pinecone import search_vectors
from app.core.openai import get_embedding
from datetime import date, timedelta

logger = logging.getLogger(__name__)


# ============================================
# 진입점
# ============================================

async def handle_action(
        request: ActionRequest,
        company_id: str,
        member_id: str,
        member_position_id: str,
        authorization: str) -> ActionResponse:
    """챗봇 액션 메인 핸들러"""
    logger.info(f"[action] ========================================")
    logger.info(
        f"[action] question='{request.question}', "
        f"session_id={request.session_id}, action={request.action}"
    )

    # 0. 액션 진행 중 사용자가 텍스트로 취소 의도 입력
    if request.session_id and request.question and not request.action:
        if _is_cancel_intent(request.question):
            logger.info(f"[action] 취소 키워드 감지로 세션 종료")
            state.clear_state(request.session_id)
            return ActionResponse(
                type="cancelled",
                message="결재 작성을 취소했습니다. 새로운 질문을 입력해주세요.",
                session_id=None
            )

    # 1. 사용자가 버튼 누른 경우
    if request.action and request.session_id:
        return await _handle_user_action(
            request, company_id, member_id, member_position_id
        )

    # 2. 신규 또는 진행 중 세션
    current_state = _load_or_create_state(
        request.session_id, company_id, member_id, member_position_id
    )

    # 3. 양식 매칭 (첫 호출)
    if not current_state.document_id:
        document = await _match_form(request.question, company_id)
        if not document:
            return _error_response(
                "어떤 결재 양식을 작성하시려는지 명확히 알려주세요. "
                "예: '연차신청서', '출장신청'",
                current_state.session_id
            )
        current_state.document_id = document["documentId"]
        current_state.document_name = document["documentName"]
        current_state.form_schema = document["formSchema"]
        current_state.request_type = document.get("requestType")

    # 4. Function Calling으로 슬롯 추출
    extracted = await _extract_slots(
        question=request.question,
        history=request.conversation_history,
        existing_slots=current_state.slots,
        form_schema=current_state.form_schema,
        document_name=current_state.document_name
    )
    if extracted:
        current_state.slots.update(extracted)

    # 5. 부족한 필수 필드 확인
    missing = form_builder.find_missing_required_fields(
        current_state.slots, current_state.form_schema
    )

    if missing:
        state.save_state(current_state)
        return _ask_response(current_state, missing)

    # 6. 모든 슬롯 채워짐 → 확인 단계
    state.save_state(current_state)
    return _confirm_response(current_state)


# ============================================
# 사용자 액션 처리 (버튼 클릭)
# ============================================

async def _handle_user_action(
        request: ActionRequest,
        company_id: str,
        member_id: str,
        member_position_id: str) -> ActionResponse:
    """사용자가 버튼 누른 경우 처리"""
    current_state = state.load_state(request.session_id)

    if not current_state:
        return _error_response("세션이 만료되었습니다. 다시 시작해주세요.", None)

    # 보안 검증
    if current_state.member_id != member_id:
        logger.warning(
            f"[action] 세션 도용 시도: owner={current_state.member_id}, "
            f"requester={member_id}"
        )
        return _error_response("세션에 접근할 권한이 없습니다.", None)

    action = request.action

    if action == "go_to_form":
        return _redirect_response(current_state)

    if action == "cancel":
        state.clear_state(request.session_id)
        return ActionResponse(
            type="cancelled",
            message="작성이 취소되었습니다.",
            session_id=request.session_id
        )

    return _error_response(f"알 수 없는 액션: {action}", request.session_id)


# ============================================
# 양식 매칭 (RAG + 키워드 re-ranking)
# ============================================

# 결재 양식명 키워드 그룹 (같은 그룹은 동의어 취급, 다른 그룹과는 구분)
# "휴가"와 "휴직"이 임베딩에서 유사하게 나오는 문제를 보정하기 위함.
FORM_KEYWORD_GROUPS = [
    ["휴가", "연차", "반차", "월차", "유급휴가"],
    ["휴직"],
    ["사직", "퇴사", "퇴직"],
    ["출장", "외근"],
    ["공문"],
    ["업무보고", "보고서"],
    ["근로계약", "연봉계약", "계약서"],
    ["수당"],
    ["근무시간", "출퇴근시간"],
]


def _rerank_by_keyword(matches, question: str):
    """
    Pinecone 벡터 유사도 결과를 키워드 매칭으로 재정렬.

    규칙:
    - 양식명 전체가 질문에 포함되면 +0.5 (강한 boost)
    - 양식명 키워드와 질문 키워드가 같은 그룹이면 +0.2
    - 양식명에는 다른 그룹의 키워드가 있는데 질문엔 없으면 -0.15 (penalty)

    예: "다음주에 휴가 갈 건데 신청해줘"
      - 휴직 신청서: 휴직 키워드 양식엔 있고 질문엔 없음 → -0.15
      - 휴가신청서: 휴가 그룹 모두 매칭 → +0.2
    """
    question_norm = question.replace(' ', '').lower()

    for m in matches:
        title = (m.metadata.get('subcategory') or '').lower()
        title_norm = title.replace(' ', '')
        original_score = m.score

        # 1. 양식명 전체가 질문에 포함되면 강한 boost (확정 매칭)
        if title_norm and title_norm in question_norm:
            m.score += 0.5
            logger.info(
                f"[rerank] '{title}' 양식명 직접 매칭 +0.5 "
                f"({original_score:.3f} → {m.score:.3f})"
            )
            continue

        # 2. 키워드 그룹 매칭 + 다른 그룹 penalty
        matched_boost = 0.0
        mismatch_penalty = 0.0

        for group in FORM_KEYWORD_GROUPS:
            in_question = any(kw in question for kw in group)
            in_title = any(kw in title for kw in group)

            if in_question and in_title:
                # 양쪽 다 있음 → 같은 양식
                matched_boost = max(matched_boost, 0.2)
            elif in_title and not in_question:
                # 양식명에는 있는데 질문엔 없음 → 다른 양식일 가능성
                mismatch_penalty = max(mismatch_penalty, 0.15)

        if matched_boost or mismatch_penalty:
            m.score = m.score + matched_boost - mismatch_penalty
            logger.info(
                f"[rerank] '{title}' boost +{matched_boost:.2f} "
                f"penalty -{mismatch_penalty:.2f} "
                f"({original_score:.3f} → {m.score:.3f})"
            )

    matches.sort(key=lambda m: m.score, reverse=True)

    logger.info(f"[rerank] 재정렬 결과:")
    for i, m in enumerate(matches):
        logger.info(
            f"  #{i+1} score={m.score:.3f} [{m.metadata.get('subcategory', '?')}]"
        )

    return matches


async def _match_form(question: str, company_id: str) -> Optional[dict]:
    """RAG로 양식 매칭 후 approval-service에서 최신 정보 조회"""
    logger.info(f"[action] 양식 매칭: '{question}'")

    query_vector = get_embedding(question)
    matches = search_vectors(
        query_vector,
        company_id,
        top_k=5,  # re-rank 위해 5개로 확장
        min_score=0.3,
        category="결재",
        include_platform=False
    )

    if not matches:
        logger.warning(f"[action] 양식 매칭 실패")
        return None

    # 키워드 기반 re-rank
    matches = _rerank_by_keyword(matches, question)

    top_match = matches[0]
    metadata = top_match.metadata
    document_name_field = metadata.get("document_name", "")
    match = re.search(
        r"approval_form_([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})",
        document_name_field
    )

    if not match:
        logger.warning(f"[action] 파일명에서 documentId 추출 실패: {document_name_field}")
        return None

    document_id = match.group(1)
    logger.info(
        f"[action] 최종 매칭: {metadata.get('subcategory')} "
        f"(score={top_match.score:.3f}, document_id={document_id})"
    )

    document = await approval_client.get_document_by_id(company_id, document_id)
    if not document:
        logger.error(f"[action] 양식 단건 조회 실패: {document_id}")
        return None

    return document


# ============================================
# Function Calling 슬롯 추출
# ============================================

async def _extract_slots(
        question: str,
        history: str,
        existing_slots: dict,
        form_schema: str,
        document_name: str) -> dict:
    """LLM Function Calling으로 사용자 입력에서 슬롯값 추출"""
    document = {"documentName": document_name, "formSchema": form_schema}
    tool = form_builder.build_tool_from_form(document)
    if not tool:
        return {}

    today = date.today()
    tomorrow = today + timedelta(days=1)
    day_after = today + timedelta(days=2)

    system_prompt = f"""당신은 HR 결재 양식 작성 어시스턴트입니다.

[현재 날짜 정보]
- 오늘: {today.isoformat()} ({_get_weekday_korean(today)})
- 내일: {tomorrow.isoformat()}
- 모레: {day_after.isoformat()}

[작성 중인 양식]
{document_name}

[이미 입력된 정보]
{json.dumps(existing_slots, ensure_ascii=False) if existing_slots else "(없음)"}

[절대 규칙 - 가장 중요]
- 사용자 입력에 **명시적으로 언급되지 않은 정보는 절대 추출하지 마세요.**
- 추측, 추론, 기본값 사용 모두 금지입니다.
- select 필드의 옵션이 사용자 입력에 없으면 그 필드는 비워두세요. "기타" 같은 기본값 사용 금지.
- 사용자가 날짜를 명시하지 않았으면 시작일/종료일을 임의로 정하지 마세요.
- 사용자가 사유를 명시하지 않았으면 reason/사유 필드를 임의로 채우지 마세요.

[허용된 자동 생성]
- title 필드: 양식 내용을 바탕으로 자연스러운 제목 자동 생성 가능
- usageDays 필드: 시작일과 종료일이 모두 있을 때 영업일 기준 자동 계산

[일반 규칙]
- 추출 가능한 정보가 없으면 함수를 호출하지 마세요.
- 날짜는 반드시 위의 [현재 날짜 정보]를 기준으로 YYYY-MM-DD로 변환하세요.
  - "오늘" → {today.isoformat()}
  - "내일" → {tomorrow.isoformat()}
  - "모레" → {day_after.isoformat()}
  - "다음주 월요일", "이번주 금요일" 등도 [현재 날짜 정보] 기준으로 계산
- 시간은 HH:mm 형식으로 변환하세요.
- 이미 입력된 정보는 유지하고, 새 정보만 추출하세요.

[빈 입력 처리]
사용자가 단순히 "사직서 작성해줘", "연차 신청해줘"처럼 양식만 요청한 경우:
- title 외의 모든 필드는 비워두세요.
- 함수를 호출하지 않거나, title 하나만 추출하세요.

[잘못된 추출 예시 - 절대 하지 말 것]
입력: "사직서 작성해줘"
잘못된 추출: {{ "title": "사직서", "resignDate": "2026-05-02", "resignReason": "기타" }}
올바른 추출: {{ "title": "사직서" }} 또는 함수 호출 안 함

입력: "연차 신청"
잘못된 추출: {{ "startDate": "2026-05-01", "endDate": "2026-05-01", "vacationType": "연차" }}
올바른 추출: {{ "title": "연차 신청서" }} 또는 함수 호출 안 함
"""

    messages = [{"role": "system", "content": system_prompt}]
    if history:
        messages.append({"role": "user", "content": f"[이전 대화]\n{history}"})
    messages.append({"role": "user", "content": f"[현재 입력]\n{question}"})

    try:
        response = openai_client.chat.completions.create(
            model="gpt-4o-mini",
            messages=messages,
            tools=[tool],
            tool_choice="auto",
            temperature=0.0
        )

        message = response.choices[0].message
        if not message.tool_calls:
            logger.info(f"[action] 추출된 슬롯 없음")
            return {}

        tool_call = message.tool_calls[0]
        arguments = json.loads(tool_call.function.arguments)
        cleaned = {k: v for k, v in arguments.items() if v is not None and v != ""}
        logger.info(f"[action] 추출된 슬롯: {cleaned}")
        return cleaned

    except Exception as e:
        logger.error(f"[action] 슬롯 추출 실패: {e}")
        return {}


# ============================================
# 헬퍼: 상태 로드/생성
# ============================================

def _load_or_create_state(
        session_id: Optional[str],
        company_id: str,
        member_id: str,
        member_position_id: str) -> ActionState:
    """기존 세션 로드 or 신규 생성"""
    if session_id:
        existing = state.load_state(session_id)
        if existing:
            if existing.member_id != member_id:
                logger.warning(
                    f"[action] 세션 도용 시도: owner={existing.member_id}, "
                    f"requester={member_id}"
                )
                new_session_id = state.create_session_id()
                return ActionState(
                    session_id=new_session_id,
                    company_id=company_id,
                    member_id=member_id,
                    member_position_id=member_position_id,
                    slots={}
                )
            return existing

    new_session_id = state.create_session_id()
    return ActionState(
        session_id=new_session_id,
        company_id=company_id,
        member_id=member_id,
        member_position_id=member_position_id,
        slots={}
    )


# ============================================
# 응답 빌더
# ============================================

def _ask_response(current_state: ActionState, missing: list) -> ActionResponse:
    """추가 정보 요청 응답"""
    first_missing = missing[0]
    label = first_missing["label"]

    if len(missing) == 1:
        message = f"{label}을(를) 알려주세요."
    else:
        labels = [m["label"] for m in missing]
        message = f"다음 정보를 알려주세요:\n- " + "\n- ".join(labels)

    if first_missing.get("type") == "select" and first_missing.get("options"):
        options_str = ", ".join(first_missing["options"])
        message += f"\n\n({label} 옵션: {options_str})"

    # 선택 항목 안내
    optional_labels = _get_optional_field_labels(
        current_state.form_schema, current_state.slots
    )
    if optional_labels:
        optional_str = ", ".join(optional_labels)
        message += (
            f"\n\n선택 항목 ({optional_str})은 함께 알려주시면 반영되며, "
            f"결재 화면에서도 입력 가능합니다."
        )

    message += "\n\n(다른 질문을 하시려면 \"취소\"라고 입력해주세요)"

    return ActionResponse(
        type="ask",
        message=message,
        session_id=current_state.session_id
    )


def _confirm_response(current_state: ActionState) -> ActionResponse:
    """확인 요청 응답 — 결재 화면으로 이동할지"""
    preview_fields = form_builder.render_preview(
        current_state.slots, current_state.form_schema
    )

    preview_text = "\n".join([f"- {k}: {v}" for k, v in preview_fields.items()])
    message = (
        f"다음 내용으로 [{current_state.document_name}]을(를) 작성합니다.\n\n"
        f"{preview_text}\n\n"
        f"결재 작성 화면에서 결재선·참조자·첨부파일을 마무리한 후 제출해주세요."
    )

    return ActionResponse(
        type="confirm",
        message=message,
        session_id=current_state.session_id,
        preview={
            "documentName": current_state.document_name,
            "fields": preview_fields
        },
        actions=[
            ActionButton(label="결재 화면으로 이동", value="go_to_form"),
            ActionButton(label="취소", value="cancel")
        ]
    )


def _redirect_response(current_state: ActionState) -> ActionResponse:
    """결재 작성 화면 이동 응답 — prefill 데이터 포함"""
    content_json = json.dumps(current_state.slots, ensure_ascii=False)
    redirect_url = f"/app/approvals?tab=compose&documentId={current_state.document_id}&prefill=true"

    message = "결재 작성 화면으로 이동합니다."

    # 세션 정리
    state.clear_state(current_state.session_id)

    return ActionResponse(
        type="redirect_to_form",
        message=message,
        session_id=None,
        preview={
            "documentId": current_state.document_id,
            "documentName": current_state.document_name,
            "contentJson": content_json
        },
        redirect_url=redirect_url
    )


def _error_response(message: str, session_id: Optional[str]) -> ActionResponse:
    return ActionResponse(
        type="error",
        message=message,
        session_id=session_id,
        error_code="GENERIC_ERROR"
    )


def _get_weekday_korean(target_date: date) -> str:
    weekdays = ["월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"]
    return weekdays[target_date.weekday()]


def _get_optional_field_labels(form_schema_str: str, slots: dict) -> list:
    """선택 필드 라벨 목록 (이미 채워진 건 제외)"""
    fields = form_builder.parse_form_schema(form_schema_str)
    visible_fields = form_builder.get_visible_fields(fields)

    optional_labels = []
    for field in visible_fields:
        # 조건부 필드는 조건 만족 시에만 검사
        if not form_builder.is_field_visible(field, slots):
            continue

        # 필수 필드는 제외
        if field.get("required"):
            continue

        # 이미 입력된 값 있으면 제외
        name = field.get("name")
        value = slots.get(name)
        if value:
            continue

        label = field.get("label", name)
        if label:
            optional_labels.append(label)

    return optional_labels


def _is_cancel_intent(question: str) -> bool:
    """사용자 입력이 취소/중단 의도인지 감지"""
    cancel_keywords = [
        "취소", "그만", "안할래", "안할게", "관둬", "중단",
        "됐어", "필요없어", "그만할래", "스톱", "stop",
        "다른 질문", "다른거", "다른 거"
    ]
    question_lower = question.strip().lower()
    return any(kw in question_lower for kw in cancel_keywords)