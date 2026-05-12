from app.core.openai import get_embedding, expand_query, detect_category
from app.core.pinecone import search_vectors
from datetime import date, datetime, timedelta, timezone
import asyncio
import logging

logger = logging.getLogger(__name__)

NO_CONTEXT_MARKER = "__NO_RELEVANT_DOCUMENT__"

# KST 타임존 (컨테이너가 UTC라 명시 필요)
KST = timezone(timedelta(hours=9))

# ============================================================
# 액션 가이드 라우팅 테이블
# ------------------------------------------------------------
# 룰 평가:
#   - subjects 중 하나라도 질문에 포함 AND
#   - actions  중 하나라도 질문에 포함 AND
#   - exclude  중 하나도 질문에 포함되지 않음
#   → document_name 으로 라우팅
# is_hr=True 라우트는 인사팀(is_hr_admin=True)에게만 매칭됨
#
# 순서 중요: HR 라우트를 일반 라우트보다 먼저 평가해야
# "신규 입사자 급여 어디서 등록?" 같은 질문이 일반 payroll 라우트가 아닌
# hr_payroll_admin 라우트로 매칭됨.
# ============================================================
ACTION_GUIDE_ROUTES = [
    # ==================== HR 전용 (is_hr=True, 인사팀만) ====================

    # ---- [HR-구성원] ----
    {
        "name": "hr_member_register",
        "subjects": ["직원 등록", "직원 추가", "직원 계정", "구성원 등록",
                     "신규 직원", "신입사원 등록"],
        "actions": ["어디", "어떻게", "방법", "추가", "등록", "생성"],
        "exclude": [],
        "document_name": "guide_hr_members.txt",
        "is_hr": True,
    },
    {
        "name": "hr_member_update",
        "subjects": ["직원 정보", "인사 정보", "직원 수정", "직원 이력",
                     "조직 이동", "휴직 처리", "복직 처리", "잠금 해제",
                     "비밀번호 잠금"],
        "actions": ["수정", "변경", "어디", "어떻게", "처리", "조회"],
        "exclude": [],
        "document_name": "guide_hr_members.txt",
        "is_hr": True,
    },

    # ---- [HR-조직] ----
    {
        "name": "hr_organization",
        "subjects": ["조직 관리", "조직 구조", "부서 생성", "부서 추가", "부서 삭제",
                     "직급 관리", "직책 관리", "역할 권한", "권한 설정",
                     "조직 개편", "인사 발령", "발령 시뮬"],
        "actions": ["어디", "어떻게", "추가", "수정", "삭제", "만들", "변경", "설정"],
        "exclude": [],
        "document_name": "guide_hr_organization.txt",
        "is_hr": True,
    },

    # ---- [HR-계약] ----
    {
        "name": "hr_contracts",
        "subjects": ["계약 발송", "계약서 발송", "근로계약서 발송",
                     "연봉계약서 발송", "개인정보 동의서 발송", "비밀유지서약서",
                     "일괄 계약", "개별 계약"],
        "actions": ["어디", "어떻게", "보내", "발송"],
        "exclude": [],
        "document_name": "guide_hr_contracts.txt",
        "is_hr": True,
    },

    # ---- [HR-결재 관리자] ----
    {
        "name": "hr_approval_admin",
        "subjects": [
            "결재 양식", "결재양식",
            "전자 결재 양식", "전자결재 양식",
            "결재 문서 양식", "결재 양식 관리",
            "결재 양식 추가", "결재 양식 등록",
            "결재 양식 수정", "결재 양식 편집",
            "양식 만들", "양식 생성", "양식 추가",
            "정책라인", "결재 라인", "결재선", "결재 순서", "결재 단계",
            "전자계약 양식", "계약 템플릿", "계약 양식",
            "근로계약서 양식", "연봉계약서 양식",
            "인감", "회사 인감", "인감 등록", "인감 관리",
            "인감 업로드", "인감 변경"
        ],
        "actions": [
            "어디", "어떻게",
            "만들", "생성",
            "추가", "등록", "수정", "편집",
            "변경", "관리", "설정", "업로드", "비활성"
        ],
        "exclude": [],
        "document_name": "guide_hr_approval_admin.txt",
        "is_hr": True,
    },

    # ---- [HR-근태 관리] ----
    {
        "name": "hr_attendance_company",
        "subjects": ["전사 근태", "회사 전체 근태", "직원 출퇴근",
                     "초과근무 현황", "한도 초과 직원", "52시간 초과"],
        "actions": ["어디", "어떻게", "봐", "조회", "확인"],
        "exclude": [],
        "document_name": "guide_hr_attendance_company.txt",
        "is_hr": True,
    },
    {
        "name": "hr_work_schedules",
        "subjects": ["근무 스케줄 관리", "회사 출퇴근 시간", "스케줄 추가",
                     "스케줄 등록", "고정 근무제", "유연근무제 설정",
                     "시차 출퇴근 관리", "시차 슬롯", "출퇴근 슬롯",
                     "기본 슬롯", "슬롯 선택 마감일"],
        "actions": ["어디", "어떻게", "만들", "추가", "등록", "수정", "삭제", "설정", "변경"],
        "exclude": [],
        "document_name": "guide_hr_work_schedules.txt",
        "is_hr": True,
    },
    {
        "name": "hr_overtime_policy",
        "subjects": ["연장근로 정책", "야근 정책", "연장근로 한도",
                     "주 52시간 설정", "주 12시간 설정",
                     "월 연장 한도", "일 한도",
                     "사후 신청 기한", "연장근로 단위", "15분 단위", "30분 단위"],
        "actions": ["어디", "어떻게", "등록", "추가", "설정", "변경", "수정", "삭제"],
        "exclude": [],
        "document_name": "guide_hr_overtime_policy.txt",
        "is_hr": True,
    },

    # ---- [HR-휴무 관리] ----
    {
        "name": "hr_holidays",
        "subjects": ["공휴일 관리", "회사 공휴일", "법정 공휴일", "공휴일 등록",
                     "공휴일 추가", "창립기념일", "임시공휴일", "회사 휴일",
                     "유급 휴일", "무급 휴일"],
        "actions": ["어디", "어떻게", "등록", "추가", "수정", "삭제", "가져와", "불러"],
        "exclude": [],
        "document_name": "guide_hr_holidays.txt",
        "is_hr": True,
    },
    {
        "name": "hr_leave_types",
        "subjects": ["휴가 종류", "휴가 종류 관리", "휴가 종류 추가",
                     "커스텀 휴가", "리프레시 휴가", "휴가 종류 수정",
                     "휴가 순서", "기본 휴가 불러오기", "수동 휴가 부여",
                     "결혼 휴가 일수", "경조 휴가"],
        "actions": ["어디", "어떻게", "추가", "만들", "수정", "삭제", "변경", "복구", "부여"],
        "exclude": ["며칠", "얼마", "유급이야", "신청"],
        "document_name": "guide_hr_leave_types.txt",
        "is_hr": True,
    },
    {
        "name": "hr_leave_policies",
        "subjects": ["연차 정책", "연차 정책 관리", "연차 발생 기준",
                     "회계연도 기준", "입사일 기준",
                     "연차 일수 변경", "기본 연차", "연차 상한", "근속 가산",
                     "촉진제도", "연차 사용 촉진", "연차 이월", "이월 허용",
                     "미사용 연차 수당", "연차 수당", "연차 통보"],
        "actions": ["어디", "어떻게", "설정", "등록", "추가", "수정", "삭제", "변경"],
        "exclude": [],
        "document_name": "guide_hr_leave_policies.txt",
        "is_hr": True,
    },

    # ---- [HR-급여 관리] ----
    {
        "name": "hr_payroll_admin",
        "subjects": ["급여 정산", "급여 정산 관리", "이번 달 급여 명세서",
                     "급여 일괄 확정", "급여 일괄 지급", "급여 명세서 엑셀",
                     "지난 달 급여", "급여 지급 이력",
                     "신규 입사자 급여", "직원 기본급 등록", "누락된 직원 급여",
                     "상여금 지급", "정기 상여 지급", "성과급 지급", "명절 상여 지급",
                     "퇴직 정산", "퇴직금 처리",
                     "연봉 인상 등록", "연봉 변경 이력", "호봉 변경 등록", "급여 변동",
                     "자격 수당 부여", "직책 수당 부여", "수당 일괄 부여", "수당 관리"],
        "actions": ["어디", "어떻게", "처리", "지급", "등록", "확인", "부여", "만들"],
        "exclude": [],
        "document_name": "guide_hr_payroll_admin.txt",
        "is_hr": True,
    },
    {
        "name": "hr_tax_summary",
        "subjects": ["4대보험 집계", "사회보험 집계", "원천세 집계",
                     "신고용 엑셀", "4대보험 신고",
                     "국민연금 집계", "건강보험 집계", "고용보험 집계",
                     "산재보험 집계", "소득세 집계",
                     "회사 부담금", "직원 부담금"],
        "actions": ["어디", "어떻게", "봐", "다운", "다운로드", "확인"],
        "exclude": [],
        "document_name": "guide_hr_tax_summary.txt",
        "is_hr": True,
    },
    {
        "name": "hr_salary_policy",
        "subjects": ["급여 정책", "회사 급여 정책",
                     "지급일 설정", "지급일 변경", "월급 지급일",
                     "임금체계", "호봉제 설정", "연봉제 설정",
                     "포괄임금제", "비포괄임금제", "고정 OT",
                     "월 소정근로시간", "209시간",
                     "일할계산", "당월분 지급", "전월분 지급",
                     "호봉표", "호봉표 관리", "호봉별 기본급",
                     "수당 항목 등록", "지급 항목", "비과세 항목",
                     "통상임금 항목"],
        "actions": ["어디", "어떻게", "설정", "변경", "등록", "추가", "수정"],
        "exclude": ["조회"],
        "document_name": "guide_hr_salary_policy.txt",
        "is_hr": True,
    },
    {
        "name": "hr_retirement_policy",
        "subjects": ["퇴직급여 정책", "퇴직금 정책", "퇴직 제도",
                     "DC형", "확정기여형", "DB형", "확정급여형",
                     "법정 퇴직금", "사내 적립",
                     "DC 부담금 비율", "8.33", "퇴직연금 운용사",
                     "퇴직연금 계약번호", "중간정산 허용"],
        "actions": ["어디", "어떻게", "설정", "등록", "변경", "수정", "삭제"],
        "exclude": [],
        "document_name": "guide_hr_retirement_policy.txt",
        "is_hr": True,
    },
    {
        "name": "hr_bonus_policy",
        "subjects": ["상여금 정책", "보너스 정책",
                     "정기상여 비율", "정기상여 횟수", "분기 상여", "반기 상여",
                     "성과급 정책", "성과급 최대", "평가 등급별 성과급", "S등급 성과급",
                     "명절 상여 정액", "명절 상여 비율", "명절 보너스 설정",
                     "추석 보너스", "설날 보너스",
                     "정규직만 상여", "신입 상여 제외", "휴직자 상여 제외"],
        "actions": ["어디", "어떻게", "설정", "등록", "변경", "수정", "삭제"],
        "exclude": ["지급해", "지급 해줘", "지급할게"],
        "document_name": "guide_hr_bonus_policy.txt",
        "is_hr": True,
    },

    # ---- [HR-ESG] ----
    {
        "name": "hr_esg",
        "subjects": ["ESG 운영", "ESG 설정", "ESG 활성화", "ESG 켜",
                     "ESG 끄", "월간 포인트 한도", "ESG 활동 양식",
                     "활동 양식 추가", "환경 활동 양식", "봉사활동 양식",
                     "활동 승인", "직원 활동 승인", "활동 반려",
                     "샵 물품 등록", "포인트 샵 물품",
                     "ESG 재고", "주문 이력", "ESG 등급", "ESG 점수",
                     "월별 ESG 성과"],
        "actions": ["어디", "어떻게", "설정", "켜", "끄", "추가", "등록", "수정", "삭제",
                    "승인", "반려", "봐", "확인"],
        "exclude": [],
        "document_name": "guide_hr_esg_admin.txt",
        "is_hr": True,
    },

    # ==================== 일반 직원도 답변 가능 (is_hr=False) ====================

    # ---- [근태] guide_attendance.txt ----
    {
        "name": "overtime_request",
        "subjects": ["야근", "초과근무", "연장근무", "잔근", "야간근로", "휴일근로"],
        "actions": ["신청", "어디", "어떻게", "방법", "올려", "제출"],
        "exclude": ["수당", "얼마", "정책"],
        "document_name": "guide_attendance.txt",
        "is_hr": False,
    },
    {
        "name": "attendance_correction",
        "subjects": ["근태 정정", "근태정정", "출근 누락", "퇴근 누락",
                     "출근 정정", "퇴근 정정", "출퇴근 정정"],
        "actions": ["신청", "어디", "어떻게", "방법", "처리", "수정"],
        "exclude": [],
        "document_name": "guide_attendance.txt",
        "is_hr": False,
    },
    {
        "name": "leave_balance_view",
        "subjects": ["잔여 휴가", "잔여 연차", "남은 휴가", "남은 연차",
                     "휴가 잔여", "연차 잔여", "내 휴가", "내 연차",
                     "사용한 휴가", "사용한 연차", "휴가 이력", "연차 이력"],
        "actions": ["어디", "어떻게", "조회", "확인", "봐", "보기", "보려"],
        "exclude": [],
        "document_name": "guide_attendance.txt",
        "is_hr": False,
    },
    {
        "name": "attendance_record_view",
        "subjects": ["출퇴근 기록", "출근 기록", "퇴근 기록", "내 근태", "근태",
                     "주간 근무시간", "월간 근무시간", "근무시간 한도"],
        "actions": ["어디", "어떻게", "조회", "확인", "봐", "보기"],
        "exclude": ["스케줄", "점심", "전사", "회사 전체"],
        "document_name": "guide_attendance.txt",
        "is_hr": False,
    },
    {
        "name": "checkin_checkout",
        "subjects": ["출근", "퇴근"],
        "actions": ["찍", "어디", "어떻게", "방법"],
        "exclude": ["기록", "시간 몇", "수당", "스케줄", "정정", "누락"],
        "document_name": "guide_attendance.txt",
        "is_hr": False,
    },
    {
        "name": "schedule_change",
        "subjects": ["스케줄 변경", "근무 시간 변경"],
        "actions": ["신청", "어디", "어떻게", "방법"],
        "exclude": ["관리"],
        "document_name": "guide_attendance_schedule.txt",
        "is_hr": False,
    },
    {
        "name": "schedule_view",
        "subjects": ["근무 스케줄", "내 스케줄", "스케줄",
                     "점심 시간", "점심시간", "주간 근로시간", "1주 근로시간"],
        "actions": ["어디", "어떻게", "조회", "확인", "봐", "보기"],
        "exclude": ["관리", "추가", "등록", "수정", "삭제"],
        "document_name": "guide_attendance_schedule.txt",
        "is_hr": False,
    },

    # ---- [결재] ----
    {
        "name": "my_approvals",
        "subjects": ["내가 올린 결재", "내 기안", "내가 신청한 결재",
                     "결재 진행 상태", "결재 회수", "임시 저장한",
                     "임시저장한", "임시 저장함", "반려된 결재"],
        "actions": ["어디", "어떻게", "봐", "조회", "확인", "회수", "다시"],
        "exclude": [],
        "document_name": "guide_approval_my.txt",
        "is_hr": False,
    },
    {
        "name": "pending_approvals",
        "subjects": ["결재 대기", "내가 결재", "결재할 문서",
                     "결재할 거", "결재 알림"],
        "actions": ["어디", "어떻게", "봐", "처리", "승인", "반려"],
        "exclude": ["양식", "라인"],
        "document_name": "guide_approval_pending.txt",
        "is_hr": False,
    },
    {
        "name": "leave_request",
        "subjects": ["휴가", "연차", "반차", "병가", "경조",
                     "출산 휴가", "예비군 휴가", "민방위 휴가", "결혼 휴가"],
        "actions": ["신청", "올려", "제출"],
        "exclude": ["며칠", "얼마", "유급", "잔여", "남은", "이력",
                    "사용한", "수당", "정책", "종류 관리", "종류 추가"],
        "document_name": "guide_approval_compose.txt",
        "is_hr": False,
    },
    {
        "name": "other_approval",
        "subjects": ["사직서", "출장", "휴직", "공문",
                     "업무기안", "업무보고서", "근로계약서",
                     "수당 변경", "출퇴근시간 변경"],
        "actions": ["작성", "신청", "어디", "어떻게", "올려", "제출"],
        "exclude": ["양식 관리", "양식 추가"],
        "document_name": "guide_approval_compose.txt",
        "is_hr": False,
    },
    {
        "name": "approval_general",
        "subjects": ["결재"],
        "actions": ["올려", "올리기", "올리는", "작성",
                    "어떻게 작성", "어떻게 올려", "어디서 작성",
                    "어디서 시작", "처음 어떻게", "방법", "양식"],
        "exclude": ["대기", "내가 결재", "내가 올린", "내 기안",
                    "진행 상태", "회수", "반려",
                    "양식 관리", "양식 추가", "양식 등록",
                    "양식 만들", "양식 생성", "양식 수정", "양식 편집",
                    "결재 양식", "결재양식",
                    "전자 결재 양식", "전자결재 양식",
                    "결재 문서 양식",
                    "라인", "정책라인", "결재선",
                    "전자계약", "계약 양식", "계약 템플릿",
                    "인감",
                    "관리자"],
        "document_name": "guide_approval_compose.txt",
        "is_hr": False,
    },

    # ---- [평가] ----
    {
        "name": "self_evaluation",
        "subjects": ["자기평가", "자기 평가", "내 평가 작성"],
        "actions": ["어디", "어떻게", "작성", "방법", "쓰", "입력"],
        "exclude": [],
        "document_name": "guide_evaluation_self.txt",
        "is_hr": False,
    },
    {
        "name": "calibration",
        "subjects": ["캘리브레이션"],
        "actions": ["어디", "어떻게", "처리", "방법"],
        "exclude": [],
        "document_name": "guide_evaluation_calibration.txt",
        "is_hr": False,
    },
    {
        "name": "evaluation_season",
        "subjects": ["평가 시즌", "시즌 단계", "평가 단계", "시즌 진행"],
        "actions": ["어디", "어떻게", "확인", "봐", "조회"],
        "exclude": [],
        "document_name": "guide_evaluation_lifecycle.txt",
        "is_hr": False,
    },

    # ---- [목표] ----
    {
        "name": "goal_approval",
        "subjects": ["목표 승인", "목표 승인 요청", "목표 승인 처리"],
        "actions": ["어디", "어떻게", "방법", "처리", "요청"],
        "exclude": [],
        "document_name": "guide_goal_approval.txt",
        "is_hr": False,
    },
    {
        "name": "org_goal",
        "subjects": ["조직 목표", "팀 목표"],
        "actions": ["작성", "생성", "어디", "어떻게", "만들"],
        "exclude": [],
        "document_name": "guide_goal_org.txt",
        "is_hr": False,
    },
    {
        "name": "personal_goal",
        "subjects": ["개인 목표", "내 목표"],
        "actions": ["작성", "어디", "어떻게", "방법"],
        "exclude": [],
        "document_name": "guide_goal_personal.txt",
        "is_hr": False,
    },

    # ---- [면담] ----
    {
        "name": "meeting",
        "subjects": ["면담", "1:1 면담", "평가 면담"],
        "actions": ["어디", "어떻게", "잡아", "등록", "기록", "작성"],
        "exclude": [],
        "document_name": "guide_meeting.txt",
        "is_hr": False,
    },

    # ---- [내 정보] ----
    {
        "name": "my_account",
        "subjects": ["계좌", "계좌번호", "은행", "통장", "급여 계좌"],
        "actions": ["수정", "변경", "바꾸", "어디", "어떻게", "등록"],
        "exclude": [],
        "document_name": "guide_my_info.txt",
        "is_hr": False,
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
        "is_hr": False,
    },

    # ---- [급여 - 일반 직원] ----
    {
        "name": "annual_salary",
        "subjects": ["연봉", "호봉", "직급"],
        "actions": ["어디", "어떻게", "조회", "확인", "봐", "보기"],
        "exclude": ["호봉표", "정책"],
        "document_name": "guide_payroll_annual.txt",
        "is_hr": False,
    },
    {
        "name": "payroll",
        "subjects": ["급여", "월급", "급여명세서", "월급명세서",
                     "명세서", "봉급", "실수령액", "급여 명세", "월급 명세"],
        "actions": ["어디", "어떻게", "조회", "확인", "봐", "보기", "다운로드"],
        "exclude": ["언제", "지급일", "들어와", "정책", "정산 관리", "변동", "등록 화면"],
        "document_name": "guide_payroll.txt",
        "is_hr": False,
    },
]


def route_to_guide(question: str, is_hr_admin: bool = False) -> str | None:
    """
    질문에서 ACTION_GUIDE_ROUTES 룰을 평가해 강제 라우팅할 문서명 반환.
    is_hr=True 인 라우트는 인사팀(is_hr_admin=True)에게만 매칭.
    """
    if not question:
        return None
    q = question.strip()

    for route in ACTION_GUIDE_ROUTES:
        # HR 라우트는 인사팀만
        if route.get("is_hr", False) and not is_hr_admin:
            continue
        if any(ex in q for ex in route["exclude"]):
            continue
        if not any(s in q for s in route["subjects"]):
            continue
        if not any(a in q for a in route["actions"]):
            continue

        logger.info(
            f"[route_to_guide] '{question}' → "
            f"{route['name']} ({route['document_name']}) is_hr={route.get('is_hr', False)}"
        )
        return route["document_name"]

    return None


# Layer 우선순위
LAYER_PRIORITY = {
    "db_sync": 3,
    "hr_uploaded": 2,
    "platform": 1,
}

LAYER_BOOST = {
    "db_sync": 0.0,
    "hr_uploaded": 0.20,
    "platform": 0.20,
}


def apply_layer_priority(matches, final_top_k=5):
    if not matches:
        return matches

    seen = {}

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

            if new_priority > existing_priority:
                seen[key] = match

    deduplicated = []
    for match in seen.values():
        layer = (match.metadata or {}).get("layer", "hr_uploaded")
        boost = LAYER_BOOST.get(layer, 0.0)
        deduplicated.append({
            "match": match,
            "original_score": match.score,
            "adjusted_score": match.score + boost,
        })
    deduplicated.sort(key=lambda m: m["adjusted_score"], reverse=True)

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
            f"is_hr={md.get('is_hr', False)} "
            f"[{md.get('category')}/{md.get('subcategory')}]"
        )
    return [item["match"] for item in result]


def _build_date_info() -> str:
    """현재 KST 날짜 정보 헤더 생성 - LLM이 시점 표현 정확히 해석하도록"""
    today = datetime.now(KST).date()
    weekdays = ["월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"]
    return f"""[현재 날짜 정보 - KST 기준]
- 오늘: {today.isoformat()} ({weekdays[today.weekday()]})
- 이번달: {today.year}년 {today.month}월
- 올해: {today.year}년

위 날짜 정보를 활용해 "오늘", "내일", "이번달", "올해" 같은 시점 표현을 정확하게 해석하세요.

---

"""


async def rag_search(
        question: str,
        company_id: str,
        conversation_history: str = "",
        is_hr_admin: bool = False) -> dict:
    logger.info(f"[rag_search] ========================================")
    logger.info(f"[rag_search] Q: '{question}'")
    logger.info(f"[rag_search] is_hr_admin={is_hr_admin}")

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
            include_platform=False,
            is_hr_admin=is_hr_admin,
        )
        matches = apply_layer_priority(raw_matches, final_top_k=5)

    # 카테고리 미감지(액션 가이드 질문) → 라우팅 시도
    if not matches and not category:
        routed_doc = route_to_guide(question, is_hr_admin=is_hr_admin)
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
                is_hr_admin=is_hr_admin,
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
            include_platform=True,
            is_hr_admin=is_hr_admin,
        )
        matches = apply_layer_priority(raw_matches, final_top_k=5)

    logger.info(f"[rag_search] 최종 매칭 {len(matches)}개")
    for i, m in enumerate(matches):
        md = m.metadata
        content_preview = md.get('content', '')[:60].replace('\n', ' ')
        logger.info(
            f"  #{i+1} score={m.score:.3f} "
            f"layer={md.get('layer', '?')} "
            f"is_hr={md.get('is_hr', False)} "
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

    context = _build_date_info() + "\n\n---\n\n".join(context_parts)

    logger.info(
        f"[rag_search] context {len(context)}자, sources={list(sources)}"
    )

    return {
        "context": context,
        "sources": list(sources)
    }