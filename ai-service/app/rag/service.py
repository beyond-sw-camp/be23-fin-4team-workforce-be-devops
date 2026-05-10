from app.core.openai import get_embedding, expand_query, detect_category
from app.core.pinecone import search_vectors
from datetime import date
import asyncio
import logging

logger = logging.getLogger(__name__)

NO_CONTEXT_MARKER = "__NO_RELEVANT_DOCUMENT__"

# ============================================================
# 액션 가이드 라우팅 테이블
# ------------------------------------------------------------
# 액션 질문(어디서/어떻게/방법)은 임베딩 점수에만 의존하면 비슷한 청크들이
# 1순위를 뒤집을 수 있다. 키워드 + 액션 매칭으로 정답 가이드를 강제 지정.
#
# 매칭 규칙:
#   - subjects 중 하나라도 질문에 포함 AND
#   - actions  중 하나라도 질문에 포함 AND
#   - exclude  중 하나도 질문에 포함되지 않음
#   → document_name 으로 라우팅 (해당 문서 청크만 임베딩 검색)
#
# 위에서 아래로 순서대로 평가하며 첫 매칭 시 stop. 따라서 더 구체적인 룰이 위.
# ============================================================
ACTION_GUIDE_ROUTES = [
    # ---- [근태] guide_attendance.txt → /app/attendance ----
    {
        "name": "overtime_request",
        "subjects": ["야근", "초과근무", "연장근무", "잔근", "야간근로", "휴일근로"],
        "actions": ["신청", "어디", "어떻게", "방법", "올려", "제출"],
        "exclude": ["수당", "얼마"],
        "document_name": "guide_attendance.txt",
    },
    {
        "name": "attendance_correction",
        "subjects": ["근태 정정", "근태정정", "출근 누락", "퇴근 누락",
                     "출근 정정", "퇴근 정정", "출퇴근 정정"],
        "actions": ["신청", "어디", "어떻게", "방법", "처리", "수정"],
        "exclude": [],
        "document_name": "guide_attendance.txt",
    },
    {
        "name": "leave_balance_view",
        "subjects": ["잔여 휴가", "잔여 연차", "남은 휴가", "남은 연차",
                     "휴가 잔여", "연차 잔여", "내 휴가", "내 연차",
                     "사용한 휴가", "사용한 연차", "휴가 이력", "연차 이력"],
        "actions": ["어디", "어떻게", "조회", "확인", "봐", "보기", "보려"],
        "exclude": [],
        "document_name": "guide_attendance.txt",
    },
    {
        "name": "attendance_record_view",
        "subjects": ["출퇴근 기록", "출근 기록", "퇴근 기록", "내 근태", "근태",
                     "주간 근무시간", "월간 근무시간", "근무시간 한도"],
        "actions": ["어디", "어떻게", "조회", "확인", "봐", "보기"],
        "exclude": ["스케줄", "점심"],
        "document_name": "guide_attendance.txt",
    },
    {
        "name": "checkin_checkout",
        "subjects": ["출근", "퇴근"],
        "actions": ["찍", "어디", "어떻게", "방법"],
        "exclude": ["기록", "시간 몇", "수당", "스케줄", "정정", "누락"],
        "document_name": "guide_attendance.txt",
    },

    # ---- [근태] guide_attendance_schedule.txt → /app/attendance/schedules/my ----
    {
        "name": "schedule_change",
        "subjects": ["스케줄 변경", "근무 시간 변경"],
        "actions": ["신청", "어디", "어떻게", "방법"],
        "exclude": [],
        "document_name": "guide_attendance_schedule.txt",
    },
    {
        "name": "schedule_view",
        "subjects": ["근무 스케줄", "내 스케줄", "스케줄",
                     "점심 시간", "점심시간", "주간 근로시간", "1주 근로시간"],
        "actions": ["어디", "어떻게", "조회", "확인", "봐", "보기"],
        "exclude": [],
        "document_name": "guide_attendance_schedule.txt",
    },

    # ---- [결재] guide_approval_my.txt → /app/approvals (내 기안 문서) ----
    {
        "name": "my_approvals",
        "subjects": ["내가 올린 결재", "내 기안", "내가 신청한 결재",
                     "결재 진행 상태", "결재 회수", "임시 저장한",
                     "임시저장한", "임시 저장함", "반려된 결재"],
        "actions": ["어디", "어떻게", "봐", "조회", "확인", "회수", "다시"],
        "exclude": [],
        "document_name": "guide_approval_my.txt",
    },

    # ---- [결재] guide_approval_pending.txt → /app/approvals (결재 대기) ----
    {
        "name": "pending_approvals",
        "subjects": ["결재 대기", "내가 결재", "결재할 문서",
                     "결재할 거", "결재 알림"],
        "actions": ["어디", "어떻게", "봐", "처리", "승인", "반려"],
        "exclude": [],
        "document_name": "guide_approval_pending.txt",
    },

    # ---- [결재] guide_approval_compose.txt → /app/approvals (결재 작성) ----
    {
        "name": "leave_request",
        "subjects": ["휴가", "연차", "반차", "병가", "경조",
                     "출산 휴가", "예비군 휴가", "민방위 휴가", "결혼 휴가"],
        "actions": ["신청", "올려", "제출"],
        "exclude": ["며칠", "얼마", "유급", "잔여", "남은", "이력",
                    "사용한", "수당"],
        "document_name": "guide_approval_compose.txt",
    },
    {
        "name": "other_approval",
        "subjects": ["사직서", "출장", "휴직", "공문",
                     "업무기안", "업무보고서", "근로계약서",
                     "수당 변경", "출퇴근시간 변경"],
        "actions": ["작성", "신청", "어디", "어떻게", "올려", "제출"],
        "exclude": [],
        "document_name": "guide_approval_compose.txt",
    },
    {
        "name": "approval_general",
        "subjects": ["결재"],
        "actions": ["올려", "올리기", "올리는", "작성",
                    "어떻게 작성", "어떻게 올려", "어디서 작성",
                    "어디서 시작", "처음 어떻게", "방법", "양식"],
        "exclude": ["대기", "내가 결재", "내가 올린", "내 기안",
                    "진행 상태", "회수", "반려"],
        "document_name": "guide_approval_compose.txt",
    },

    # ---- [평가] /app/evaluations ----
    {
        "name": "self_evaluation",
        "subjects": ["자기평가", "자기 평가", "내 평가 작성"],
        "actions": ["어디", "어떻게", "작성", "방법", "쓰", "입력"],
        "exclude": [],
        "document_name": "guide_evaluation_self.txt",
    },
    {
        "name": "calibration",
        "subjects": ["캘리브레이션"],
        "actions": ["어디", "어떻게", "처리", "방법"],
        "exclude": [],
        "document_name": "guide_evaluation_calibration.txt",
    },
    {
        "name": "evaluation_season",
        "subjects": ["평가 시즌", "시즌 단계", "평가 단계", "시즌 진행"],
        "actions": ["어디", "어떻게", "확인", "봐", "조회"],
        "exclude": [],
        "document_name": "guide_evaluation_lifecycle.txt",
    },

    # ---- [목표] /app/performance ----
    {
        "name": "goal_approval",
        "subjects": ["목표 승인", "목표 승인 요청", "목표 승인 처리"],
        "actions": ["어디", "어떻게", "방법", "처리", "요청"],
        "exclude": [],
        "document_name": "guide_goal_approval.txt",
    },
    {
        "name": "org_goal",
        "subjects": ["조직 목표", "팀 목표"],
        "actions": ["작성", "생성", "어디", "어떻게", "만들"],
        "exclude": [],
        "document_name": "guide_goal_org.txt",
    },
    {
        "name": "personal_goal",
        "subjects": ["개인 목표", "내 목표"],
        "actions": ["작성", "어디", "어떻게", "방법"],
        "exclude": [],
        "document_name": "guide_goal_personal.txt",
    },

    # ---- [면담] guide_meeting.txt → /app/meetings ----
    {
        "name": "meeting",
        "subjects": ["면담", "1:1 면담", "평가 면담"],
        "actions": ["어디", "어떻게", "잡아", "등록", "기록", "작성"],
        "exclude": [],
        "document_name": "guide_meeting.txt",
    },

    # ---- [내 정보] guide_my_info.txt → /app/me ----
    {
        "name": "my_account",
        "subjects": ["계좌", "계좌번호", "은행", "통장", "급여 계좌"],
        "actions": ["수정", "변경", "바꾸", "어디", "어떻게", "등록"],
        "exclude": [],
        "document_name": "guide_my_info.txt",
    },
    {
        "name": "my_info",
        "subjects": ["내 정보", "프로필", "마이페이지",
                     "휴대폰", "핸드폰", "전화번호", "연락처", "비상연락처",
                     "주소", "거주지", "내선번호", "직통번호",
                     "프로필 사진", "프로필사진"],
        "actions": ["수정", "변경", "바꾸", "어디", "어떻게", "등록"],
        "exclude": [],
        "document_name": "guide_my_info.txt",
    },

    # ---- [급여] /app/payroll ----
    {
        "name": "annual_salary",
        "subjects": ["연봉", "호봉", "직급"],
        "actions": ["어디", "어떻게", "조회", "확인", "봐", "보기"],
        "exclude": ["호봉표"],
        "document_name": "guide_payroll_annual.txt",
    },
    {
        "name": "payroll",
        "subjects": ["급여", "월급", "급여명세서", "월급명세서",
                     "명세서", "봉급", "실수령액", "급여 명세", "월급 명세"],
        "actions": ["어디", "어떻게", "조회", "확인", "봐", "보기", "다운로드"],
        "exclude": ["언제", "지급일", "들어와"],
        "document_name": "guide_payroll.txt",
    },
]


def route_to_guide(question: str) -> str | None:
    """
    질문에서 ACTION_GUIDE_ROUTES 룰을 평가해 강제 라우팅할 문서명 반환.
    매칭 안 되면 None → 기존 임베딩 검색으로 폴백.
    """
    if not question:
        return None
    q = question.strip()

    for route in ACTION_GUIDE_ROUTES:
        if any(ex in q for ex in route["exclude"]):
            continue
        if not any(s in q for s in route["subjects"]):
            continue
        if not any(a in q for a in route["actions"]):
            continue

        logger.info(
            f"[route_to_guide] '{question}' → "
            f"{route['name']} ({route['document_name']})"
        )
        return route["document_name"]

    return None


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

    # 카테고리 미감지(액션 가이드 질문) → 라우팅 시도
    if not matches and not category:
        routed_doc = route_to_guide(question)
        if routed_doc:
            logger.info(
                f"[rag_search] 라우트 매칭 → {routed_doc} 문서로 한정 검색"
            )
            raw_matches = await asyncio.to_thread(
                search_vectors,
                query_vector,
                company_id,
                top_k=5,
                min_score=0.30,
                category=None,
                include_platform=True,
                document_name=routed_doc,
            )
            matches = apply_layer_priority(raw_matches, final_top_k=3)

    if not matches:
        if category:
            logger.info(
                f"[rag_search] 카테고리({category}) 매칭 없음, 전체 검색"
            )
        else:
            logger.info(f"[rag_search] 라우트 미매칭/매칭 없음, 전체 검색")

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