"""근무/근태 정책을 RAG 문서로 변환하여 Pinecone에 저장"""
import logging
from datetime import datetime
from sqlalchemy.orm import Session
from app.document.model import HrDocument
from app.document.service import process_document
from app.core.pinecone import delete_vectors
from app.sync import salary_client

logger = logging.getLogger(__name__)


WORK_TYPE_NAMES = {
    "FIXED": "고정 근무제",
    "FLEXIBLE": "유연 근무제 (시차출퇴근)",
}

APPROVAL_MODE_DESCRIPTIONS = {
    "PRE_ONLY": "사전 신청만 허용 (실제 근무 전에 결재)",
    "POST_ONLY": "사후 승인만 허용 (근무 후 신청)",
    "HYBRID": "사전·사후 둘 다 허용",
}


# ============================================
# WorkSchedule
# ============================================

def format_work_schedule_to_rag(schedule: dict) -> str:
    """하나의 근무 스케줄을 RAG 문서로 변환"""
    schedule_name = schedule.get("scheduleName", "근무 스케줄")
    work_type = schedule.get("workType", "FIXED")
    work_type_name = WORK_TYPE_NAMES.get(work_type, work_type)
    start_time = schedule.get("startTime", "09:00")
    end_time = schedule.get("endTime", "18:00")
    work_minutes = schedule.get("workMinutes", 480)
    break_minutes = schedule.get("breakMinutes", 60)
    member_id = schedule.get("memberId")
    selection_deadline_day = schedule.get("selectionDeadlineDay")

    work_hours = work_minutes / 60 if work_minutes else 0

    scope = "회사 기본 스케줄" if not member_id else "개인별 스케줄"

    flexible_note = ""
    if work_type == "FLEXIBLE" and selection_deadline_day:
        flexible_note = f"\n- 시차출퇴근 슬롯 선택 마감일: 매월 {selection_deadline_day}일"

    return f"""[근무] 출근 시간 / 퇴근 시간 / 점심 시간 안내

■ 출근 시간 / 퇴근 시간
- 출근 시간: {start_time}
- 퇴근 시간: {end_time}
- 일 근무 시간: {work_hours:g}시간

■ 점심 시간 / 휴게 시간
- 점심 시간: {break_minutes}분
- 휴게 시간: {break_minutes}분 ({break_minutes / 60:g}시간)

■ 지각 / 조기 출근 안내
- 지각 기준: 출근 시간({start_time}) 이후 출근 시 지각 처리
- 조기 출근: 출근 시간({start_time}) 전 출근 시 정시 처리
- 지각/결근 발생 시: 근태 정정 신청 결재 양식으로 신청 가능

■ 근무 유형
- {work_type_name}
- 적용 범위: {scope}{flexible_note}

■ 자주 묻는 질문
질문: 출근 시간 몇 시?
답변: {start_time}

질문: 출근 몇 시까지 가야 해?
답변: {start_time}까지

질문: 퇴근 시간 언제?
답변: {end_time}

질문: 퇴근 몇 시?
답변: {end_time}

질문: 점심 시간 몇 분?
답변: {break_minutes}분

질문: 휴게 시간 얼마?
답변: {break_minutes}분

질문: 일 근무 시간 몇 시간?
답변: {work_hours:g}시간

질문: 근무 유형이 뭐야?
답변: {work_type_name}

■ 관련 키워드
출근시간, 출근 시간, 퇴근시간, 퇴근 시간,
출근, 퇴근, 출퇴근, 출근 몇 시, 퇴근 몇 시,
점심시간, 점심 시간, 휴게시간, 휴게 시간,
근무시간, 근무 시간, 일 근무, 근무 유형,
{work_type_name}, 회사 출근, 회사 퇴근,
{schedule_name}, 몇시 출근, 몇시 퇴근,
출근 언제, 퇴근 언제, 점심 몇분, 결근, 지각,"""


# ============================================
# OvertimePolicy
# ============================================

def format_overtime_policy_to_rag(policy: dict) -> str:
    """연장근로 정책을 RAG 문서로 변환 (관리자/내부 정책 - 라우팅 없음)"""
    overtime_floor = policy.get("overtimeFloorMinutes", 30)
    approval_mode = policy.get("approvalMode", "HYBRID")
    approval_desc = APPROVAL_MODE_DESCRIPTIONS.get(approval_mode, approval_mode)
    post_deadline = policy.get("postApprovalDeadlineHours", 72)
    weekly_overtime = policy.get("weeklyOvertimeLimitMinutes", 720)
    weekly_total = policy.get("weeklyTotalLimitMinutes", 3120)
    daily_overtime = policy.get("dailyOvertimeLimitMinutes", 240)
    monthly_overtime = policy.get("monthlyOvertimeLimitMinutes")
    holiday_approval = policy.get("holidayWorkRequiresApproval", True)

    weekly_overtime_h = weekly_overtime / 60 if weekly_overtime else 0
    weekly_total_h = weekly_total / 60 if weekly_total else 0
    daily_overtime_h = daily_overtime / 60 if daily_overtime else 0

    details = []
    details.append(f"- 최소 인정 단위: {overtime_floor}분 (절사)")
    details.append(f"- 신청 방식: {approval_desc}")

    if approval_mode in ("POST_ONLY", "HYBRID"):
        details.append(f"- 사후 신청 기한: {post_deadline}시간 이내")

    details.append(f"- 주 연장근로 한도: {weekly_overtime_h:g}시간")
    details.append(f"- 주 총 근무 한도: {weekly_total_h:g}시간 (법정 52시간 기준)")
    details.append(f"- 일 연장근로 한도: {daily_overtime_h:g}시간")

    if monthly_overtime:
        monthly_overtime_h = monthly_overtime / 60
        details.append(f"- 월 연장근로 한도: {monthly_overtime_h:g}시간")

    details.append("- 야간근로 시간대: 22:00 ~ 06:00 (근로기준법 고정)")
    details.append(
        f"- 휴일 근무 결재: {'필수' if holiday_approval else '불필요'}"
    )

    rate_section = """
■ 수당 배수 (근로기준법 표준)
- 연장근로 수당: 통상임금의 1.5배
- 야간근로 수당: 통상임금의 0.5배 추가 가산 (연장과 중복 시 누적)
- 휴일근로 수당: 통상임금의 1.5배 (8시간 초과 시 2배)"""

    details_str = "\n".join(details)

    return f"""[근무] 연장근로 정책

■ 설명
주 52시간 근로기준법에 따른 연장근로 정책입니다.

■ 상세 내용
{details_str}
{rate_section}

■ 관련 키워드
연장근로, 야근, 야간근로, 휴일근로, 초과근무, 수당, 52시간, 가산수당, 1.5배, 야근 수당, 휴일근무 수당"""


# ============================================
# CompanyHoliday
# ============================================

def format_company_holidays_to_rag(holidays: list) -> str:
    """공휴일 전체 목록을 하나의 RAG 문서로 변환"""
    if not holidays:
        return ""

    sorted_holidays = sorted(
        holidays,
        key=lambda h: h.get("holidayDate", "")
    )

    legal_by_year = {}
    company_by_year = {}

    for h in sorted_holidays:
        date_str = h.get("holidayDate", "")
        try:
            year = datetime.strptime(date_str, "%Y-%m-%d").year
        except ValueError:
            year = "기타"

        # DB의 isLegalYn 필드로 정확하게 분류
        is_legal = h.get("isLegalYn", "Y") == "Y"

        if is_legal:
            legal_by_year.setdefault(year, []).append(h)
        else:
            company_by_year.setdefault(year, []).append(h)

    # 분기 섹션 생성
    sections = []

    if legal_by_year:
        sections.append("\n■ 법정 공휴일 (국가 지정)")
        for year, items in sorted(legal_by_year.items()):
            lines = [f"\n▶ {year}년 (총 {len(items)}일)"]
            for h in items:
                date = h.get("holidayDate", "")
                name = h.get("holidayName", "")
                paid = h.get("isPaidYn", "Y") == "Y"
                paid_label = "유급" if paid else "무급"
                lines.append(f"- {date} {name} ({paid_label})")
            sections.append("\n".join(lines))

    if company_by_year:
        sections.append("\n■ 회사 자체 지정 휴일")
        for year, items in sorted(company_by_year.items()):
            lines = [f"\n▶ {year}년 (총 {len(items)}일)"]
            for h in items:
                date = h.get("holidayDate", "")
                name = h.get("holidayName", "")
                paid = h.get("isPaidYn", "Y") == "Y"
                paid_label = "유급" if paid else "무급"
                lines.append(f"- {date} {name} ({paid_label})")
            sections.append("\n".join(lines))
    else:
        sections.append("\n■ 회사 자체 지정 휴일\n현재 회사가 별도로 지정한 휴일은 없습니다.")

    holidays_str = "\n".join(sections)

    total_count = len(sorted_holidays)
    legal_count = sum(len(v) for v in legal_by_year.values())
    company_count = sum(len(v) for v in company_by_year.values())

    summary = (
        f"총 {total_count}일 "
        f"(법정 공휴일 {legal_count}일, 회사 지정 {company_count}일)"
    )

    return f"""[근무] 회사 공휴일

■ 설명
회사 휴일은 두 가지로 구분됩니다.
1. 법정 공휴일: 국가가 지정한 공휴일 (신정, 설날, 추석, 대체공휴일 등)
2. 회사 자체 지정 휴일: 인사팀/관리자가 별도로 지정한 휴일

■ 요약
{summary}
{holidays_str}

■ 자주 묻는 질문
- 올해 공휴일 며칠? → 위 법정 공휴일 목록 참고
- 회사 휴일 언제? → 위 법정 + 회사 지정 휴일 목록 참고
- 회사가 지정한 휴일 있어? → 위 회사 자체 지정 휴일 섹션 참고
- 빨간날 며칠? → 법정 공휴일 + 회사 지정 휴일 합산

신정, 설날, 추석, 어린이날, 광복절, 개천절, 한글날,
성탄절, 기독탄신일, 노동절, 근로자의 날, 제헌절,
삼일절, 부처님오신날, 현충일, 전국동시지방선거"""

# ============================================
# 통합 동기화 (모든 attendance 정책)
# ============================================

async def sync_attendance_documents(
        company_id: str,
        db: Session) -> dict:
    """
    회사의 근무/근태 정책을 RAG 문서로 동기화.

    동기화 대상:
    - WorkSchedule (근무 스케줄)
    - OvertimePolicy (연장근로 정책)
    - CompanyHoliday (공휴일)
    """
    logger.info(
        f"[attendance_syncer] 동기화 시작: company={company_id}"
    )

    # 1. salary-service에서 데이터 조회
    try:
        schedules = await salary_client.get_work_schedules(company_id)
    except Exception as e:
        logger.error(f"[attendance_syncer] 근무 스케줄 조회 실패: {e}")
        schedules = []

    try:
        overtime_policies = await salary_client.get_overtime_policies(company_id)
    except Exception as e:
        logger.error(f"[attendance_syncer] 연장근로 정책 조회 실패: {e}")
        overtime_policies = []

    try:
        holidays = await salary_client.get_company_holidays(company_id)
    except Exception as e:
        logger.error(f"[attendance_syncer] 공휴일 조회 실패: {e}")
        holidays = []

    # 2. 기존 db_sync attendance 문서 삭제
    existing_docs = db.query(HrDocument).filter(
        HrDocument.company_id == company_id,
        HrDocument.layer == "db_sync",
        HrDocument.document_name.like("attendance_%"),
        HrDocument.del_yn == "NO"
    ).all()

    deleted_count = 0
    for doc in existing_docs:
        try:
            delete_vectors(doc.id)
        except Exception as e:
            logger.error(
                f"[attendance_syncer] Pinecone 삭제 실패: {doc.id}, {e}"
            )
        doc.del_yn = "YES"
        deleted_count += 1

    if deleted_count > 0:
        db.commit()
        logger.info(
            f"[attendance_syncer] 기존 문서 {deleted_count}개 삭제"
        )

    # 3. 신규 문서 생성
    synced_count = 0

    # 3-1. WorkSchedule (회사 기본 스케줄만, memberId가 null인 것)
    company_schedules = [
        s for s in schedules if not s.get("memberId")
    ]
    for schedule in company_schedules:
        schedule_id = schedule.get("workScheduleId", "unknown")
        document_name = f"attendance_schedule_{schedule_id}.auto.txt"
        content = format_work_schedule_to_rag(schedule)

        if await _save_doc(db, company_id, document_name, content):
            synced_count += 1

    # 3-2. OvertimePolicy (현재 활성 정책)
    if overtime_policies:
        # 가장 최신 effective 정책 우선 (없으면 첫 번째)
        active_policy = overtime_policies[0]
        document_name = "attendance_overtime_policy.auto.txt"
        content = format_overtime_policy_to_rag(active_policy)

        if await _save_doc(db, company_id, document_name, content):
            synced_count += 1

    # 3-3. CompanyHoliday (전체 목록 하나로 묶음)
    if holidays:
        document_name = "attendance_holidays.auto.txt"
        content = format_company_holidays_to_rag(holidays)

        if content and await _save_doc(db, company_id, document_name, content):
            synced_count += 1

    logger.info(
        f"[attendance_syncer] 동기화 완료: "
        f"company={company_id}, synced={synced_count}, deleted={deleted_count}"
    )

    return {
        "synced": synced_count,
        "deleted": deleted_count
    }


async def _save_doc(
        db: Session,
        company_id: str,
        document_name: str,
        content: str) -> bool:
    """DB 저장 + Pinecone 업로드 공통 헬퍼"""
    new_doc = HrDocument(
        company_id=company_id,
        document_name=document_name,
        content=content,
        layer="db_sync"
    )
    db.add(new_doc)
    db.commit()
    db.refresh(new_doc)

    try:
        await process_document(
            new_doc.id,
            company_id,
            document_name,
            content,
            layer="db_sync"
        )
        logger.info(f"[attendance_syncer] 업로드 완료: {document_name}")
        return True
    except Exception as e:
        logger.error(
            f"[attendance_syncer] 업로드 실패: {document_name}, {e}"
        )
        db.delete(new_doc)
        db.commit()
        return False