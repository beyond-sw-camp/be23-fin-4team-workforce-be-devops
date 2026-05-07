"""급여 정책을 RAG 문서로 변환하여 Pinecone에 저장"""
import logging
from sqlalchemy.orm import Session
from app.document.model import HrDocument
from app.document.service import process_document
from app.core.pinecone import delete_vectors
from app.sync import salary_client

logger = logging.getLogger(__name__)


# Enum → 한국어 매핑
ITEM_TYPE_NAMES = {
    "EARNING": "지급",
    "DEDUCTION": "공제",
}

PAY_TYPE_NAMES = {
    "MONTHLY": "월급",
    "BONUS": "보너스",
    "SEVERANCE": "퇴직금",
}

PAY_DAY_SHIFT_NAMES = {
    "NONE": "주말/공휴일이어도 그대로 지급",
    "BEFORE": "직전 영업일로 앞당겨 지급 (실무 표준)",
    "AFTER": "직후 영업일로 미뤄 지급",
}

WAGE_SYSTEM_NAMES = {
    "COMPREHENSIVE": "포괄임금제 (기본급에 일정 OT 포함)",
    "NON_COMPREHENSIVE": "비포괄임금제 (모든 초과근무 별도 1.5배 지급)",
}


# ============================================
# SalaryItemTemplate (지급/공제 항목)
# ============================================

def format_salary_item_templates_to_rag(templates: list) -> tuple[str, str]:
    """
    급여 항목 템플릿 전체를 RAG 문서 2개로 변환:
    1. 지급 항목 (EARNING)
    2. 공제 항목 (DEDUCTION)

    Returns: (earning_content, deduction_content)
    """
    earnings = []
    deductions = []

    for tpl in templates:
        del_yn = tpl.get("delYn", "N")
        if del_yn != "N":
            continue

        item_type = tpl.get("itemType", "")
        item_name = tpl.get("itemName", "")
        is_taxable = tpl.get("isTaxableYn") == "Y"
        display_order = tpl.get("displayOrder", 999)

        info = {
            "name": item_name,
            "is_taxable": is_taxable,
            "order": display_order
        }

        if item_type == "EARNING":
            earnings.append(info)
        elif item_type == "DEDUCTION":
            deductions.append(info)

    earnings.sort(key=lambda x: x["order"])
    deductions.sort(key=lambda x: x["order"])

    earning_content = _format_earning_section(earnings)
    deduction_content = _format_deduction_section(deductions)

    return earning_content, deduction_content


def _format_earning_section(earnings: list) -> str:
    """지급 항목 RAG 문서"""
    if not earnings:
        return ""

    lines = []
    for item in earnings:
        tax_label = "과세" if item["is_taxable"] else "비과세"
        lines.append(f"- {item['name']} ({tax_label})")

    items_str = "\n".join(lines)

    return f"""[급여] 지급 항목

■ 설명
회사에서 직원에게 지급하는 급여 항목 목록입니다. 과세 여부는 항목별로 다릅니다.

■ 지급 항목 목록
{items_str}

■ 비과세 항목 안내
근로기준법에 따라 일부 항목은 비과세 처리됩니다. 일반적으로:
- 식대: 월 20만원까지 비과세
- 자가운전보조금: 월 20만원까지 비과세
- 출산·보육수당: 월 20만원까지 비과세
- 연구활동비: 월 20만원까지 비과세
- 국외 근로소득: 월 100만원까지 비과세

■ 관련 키워드
지급 항목, 수당, 기본급, 식대, 교통비, 보육수당, 비과세, 과세, 급여, 봉급"""


def _format_deduction_section(deductions: list) -> str:
    """공제 항목 RAG 문서"""
    if not deductions:
        return ""

    lines = []
    for item in deductions:
        lines.append(f"- {item['name']}")

    items_str = "\n".join(lines)

    return f"""[급여] 공제 항목

■ 설명
직원 급여에서 공제되는 항목 목록입니다. 4대 보험과 세금이 포함됩니다.

■ 공제 항목 목록
{items_str}

■ 4대 보험 표준 안내
- 국민연금: 4.5% (회사·직원 각각)
- 건강보험: 약 3.5% (회사·직원 각각)
- 장기요양보험: 건강보험료의 약 12.95%
- 고용보험: 0.9% (직원, 회사는 별도)
- 산재보험: 회사 부담만

■ 세금 표준 안내
- 소득세: 간이세액표 기준
- 지방소득세: 소득세의 10%

(세부 요율은 매년 변경되며 공제 항목명만 위 목록 기준입니다)

■ 관련 키워드
공제 항목, 4대보험, 국민연금, 건강보험, 고용보험, 산재보험, 소득세, 지방소득세, 장기요양"""


# ============================================
# SalaryPolicy (급여 정책)
# ============================================

def format_salary_policy_to_rag(policy: dict) -> str:
    """급여 정책 하나를 RAG 문서로 변환"""
    policy_name = policy.get("policyName", "급여 정책")
    pay_type = policy.get("payType", "MONTHLY")
    pay_type_name = PAY_TYPE_NAMES.get(pay_type, pay_type)
    pay_day = policy.get("payDay")
    pay_day_shift = policy.get("payDayShiftRule", "NONE")
    pay_day_shift_desc = PAY_DAY_SHIFT_NAMES.get(pay_day_shift, pay_day_shift)
    use_pay_grade = policy.get("usePayGradeYn") == "Y"
    wage_system = policy.get("wageSystemType", "NON_COMPREHENSIVE")
    wage_system_name = WAGE_SYSTEM_NAMES.get(wage_system, wage_system)
    fixed_ot_minutes = policy.get("fixedOvertimeMinutes")

    details = []
    details.append(f"- 정책명: {policy_name}")
    details.append(f"- 급여 종류: {pay_type_name}")

    if pay_day:
        details.append(f"- 지급일: 매월 {pay_day}일")
        details.append(f"- 지급일 조정 규칙: {pay_day_shift_desc}")

    details.append(f"- 호봉제 사용 여부: {'예 (호봉표 기반)' if use_pay_grade else '아니오 (연봉협상제)'}")
    details.append(f"- 임금제 형태: {wage_system_name}")

    if wage_system == "COMPREHENSIVE" and fixed_ot_minutes:
        fixed_ot_hours = fixed_ot_minutes / 60
        details.append(
            f"- 포괄 OT 한도: 월 {fixed_ot_hours:g}시간 포함 "
            f"(이를 초과한 OT만 별도 지급)"
        )

    details_str = "\n".join(details)

    keywords = [
        "급여 정책", pay_type_name, "지급일", "월급",
        "호봉제" if use_pay_grade else "연봉제",
        wage_system_name
    ]
    if pay_day:
        keywords.append(f"매월 {pay_day}일")
    keywords_str = ", ".join(keywords)

    return f"""[급여] {policy_name}

■ 설명
회사의 급여 지급 기본 정책입니다.

■ 상세 내용
{details_str}

■ 관련 키워드
{keywords_str}"""


# ============================================
# PayGradeTable (호봉표)
# ============================================

def format_pay_grade_table_to_rag(grades: list) -> str:
    """호봉표 전체를 하나의 RAG 문서로 변환"""
    if not grades:
        return ""

    sorted_grades = sorted(
        grades,
        key=lambda g: g.get("step", 0)
    )

    lines = []
    for g in sorted_grades:
        step = g.get("step", 0)
        base_salary = g.get("baseSalary", 0)
        description = g.get("description", "")

        base_salary_str = f"{base_salary:,}원" if base_salary else "0원"

        if description:
            lines.append(f"- {step}호봉: 기본급 {base_salary_str} ({description})")
        else:
            lines.append(f"- {step}호봉: 기본급 {base_salary_str}")

    grades_str = "\n".join(lines)

    return f"""[급여] 호봉표

■ 설명
회사 호봉별 기본급 테이블입니다. 호봉제를 사용하는 회사의 경우 직급/연차에 따라 정해진 호봉의 기본급이 적용됩니다.

■ 호봉별 기본급
{grades_str}

■ 관련 키워드
호봉, 호봉표, 기본급, 호봉제, 연봉, 직급, 임금테이블"""


# ============================================
# 통합 동기화
# ============================================

async def sync_salary_documents(
        company_id: str,
        db: Session) -> dict:
    """
    회사의 급여 정책을 RAG 문서로 동기화.

    동기화 대상:
    - SalaryItemTemplate (지급/공제 항목 → 2개 문서)
    - SalaryPolicy (급여 정책 → N개 문서)
    - PayGradeTable (호봉표 → 1개 문서)
    """
    logger.info(
        f"[salary_syncer] 동기화 시작: company={company_id}"
    )

    # 1. salary-service에서 데이터 조회
    try:
        item_templates = await salary_client.get_salary_item_templates(company_id)
    except Exception as e:
        logger.error(f"[salary_syncer] 급여 항목 조회 실패: {e}")
        item_templates = []

    try:
        salary_policies = await salary_client.get_salary_policies(company_id)
    except Exception as e:
        logger.error(f"[salary_syncer] 급여 정책 조회 실패: {e}")
        salary_policies = []

    try:
        pay_grades = await salary_client.get_pay_grade_tables(company_id)
    except Exception as e:
        logger.error(f"[salary_syncer] 호봉표 조회 실패: {e}")
        pay_grades = []

    # 2. 기존 db_sync salary 문서 삭제
    existing_docs = db.query(HrDocument).filter(
        HrDocument.company_id == company_id,
        HrDocument.layer == "db_sync",
        HrDocument.document_name.like("salary_%"),
        HrDocument.del_yn == "NO"
    ).all()

    deleted_count = 0
    for doc in existing_docs:
        try:
            delete_vectors(doc.id)
        except Exception as e:
            logger.error(
                f"[salary_syncer] Pinecone 삭제 실패: {doc.id}, {e}"
            )
        doc.del_yn = "YES"
        deleted_count += 1

    if deleted_count > 0:
        db.commit()
        logger.info(
            f"[salary_syncer] 기존 문서 {deleted_count}개 삭제"
        )

    # 3. 신규 문서 생성
    synced_count = 0

    # 3-1. 급여 항목 템플릿 (지급/공제 분리)
    if item_templates:
        earning_content, deduction_content = format_salary_item_templates_to_rag(
            item_templates
        )

        if earning_content:
            if await _save_doc(
                    db, company_id,
                    "salary_items_earning.auto.txt",
                    earning_content
            ):
                synced_count += 1

        if deduction_content:
            if await _save_doc(
                    db, company_id,
                    "salary_items_deduction.auto.txt",
                    deduction_content
            ):
                synced_count += 1

    # 3-2. 급여 정책 (각 정책별로 문서 1개)
    for policy in salary_policies:
        del_yn = policy.get("delYn", "N")
        if del_yn != "N" and del_yn is not None:
            continue

        policy_id = policy.get("salaryPolicyId", "unknown")
        document_name = f"salary_policy_{policy_id}.auto.txt"
        content = format_salary_policy_to_rag(policy)

        if await _save_doc(db, company_id, document_name, content):
            synced_count += 1

    # 3-3. 호봉표 (전체 한 문서)
    if pay_grades:
        document_name = "salary_pay_grade.auto.txt"
        content = format_pay_grade_table_to_rag(pay_grades)

        if content and await _save_doc(db, company_id, document_name, content):
            synced_count += 1

    logger.info(
        f"[salary_syncer] 동기화 완료: "
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
        logger.info(f"[salary_syncer] 업로드 완료: {document_name}")
        return True
    except Exception as e:
        logger.error(
            f"[salary_syncer] 업로드 실패: {document_name}, {e}"
        )
        db.delete(new_doc)
        db.commit()
        return False