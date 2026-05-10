"""휴가 정책을 RAG 문서로 변환하여 Pinecone에 저장"""
import logging
from sqlalchemy.orm import Session
from app.document.model import HrDocument
from app.document.service import process_document
from app.core.pinecone import delete_vectors
from app.sync import salary_client

logger = logging.getLogger(__name__)


LEAVE_CODE_DESCRIPTIONS = {
    "ANNUAL": "근로기준법에 따른 법정 유급 연차 휴가",
    "HALF_AM": "오전 반차 (오전 근무 시간 사용)",
    "HALF_PM": "오후 반차 (오후 근무 시간 사용)",
    "BEREAVEMENT": "본인 또는 가족의 경조사에 사용하는 특별 휴가",
    "WEDDING": "본인 결혼 시 사용 가능한 특별 휴가",
    "PUBLIC": "공가 (법원 출석, 공공 업무 등)",
    "SICK": "병가 (질병 또는 부상으로 인한 휴가)",
    "RESERVE_TRAINING": "예비군 훈련 참가 휴가",
    "CIVIL_DEFENSE": "민방위 훈련 참가 휴가",
    "MATERNITY": "출산 전후 휴가",
    "CHILDCARE": "육아 관련 휴가",
    "MENSTRUATION": "생리 휴가",
}


def format_leave_type_to_rag(
        leave_type: dict,
        annual_policy: dict = None) -> str:
    """하나의 휴가 종류를 RAG 문서 포맷으로 변환"""

    code = leave_type.get("code", "")
    name = leave_type.get("name", "휴가")
    is_paid = leave_type.get("isPaidYn") == "Y"
    max_days = leave_type.get("maxDaysPerYear")
    days_per_use = leave_type.get("daysPerUse", 1.0)
    require_evidence = leave_type.get("requireEvidenceYn") == "Y"

    description = LEAVE_CODE_DESCRIPTIONS.get(
        code, f"{name}에 해당하는 휴가입니다."
    )

    details = []
    if max_days is not None and max_days > 0:
        details.append(f"- 연간 최대 일수: {int(max_days)}일")
    if days_per_use and days_per_use != 1.0:
        details.append(f"- 1회 사용 단위: {days_per_use}일")
    details.append(f"- 유급/무급: {'유급' if is_paid else '무급'}")
    details.append(
        f"- 증빙 서류: {'필수' if require_evidence else '선택'}"
    )

    # ANNUAL이면 연차 정책 추가
    extra_policy_section = ""
    if code == "ANNUAL" and annual_policy:
        is_carryover = annual_policy.get("isCarryoverYn") == "Y"
        carryover_days = annual_policy.get("carryoverDays", 0)
        is_payout = annual_policy.get("isPayoutYn") == "Y"
        default_annual = annual_policy.get("defaultAnnualDays")
        accrual_base = annual_policy.get("accrualBase", "")

        if default_annual:
            details.append(f"- 기본 연차 부여: {int(default_annual)}일")
        if accrual_base == "FISCAL":
            details.append("- 부여 기준: 회계연도 (매년 1월 1일)")
        elif accrual_base == "HIRE_DATE":
            details.append("- 부여 기준: 입사일 기준")

        extra_lines = []
        if is_carryover:
            extra_lines.append(
                f"미사용 연차는 최대 {carryover_days}일까지 "
                f"다음 해로 이월할 수 있습니다."
            )
        else:
            extra_lines.append("미사용 연차는 이월되지 않습니다.")

        if is_payout:
            extra_lines.append(
                "연말에 미사용 연차는 수당으로 지급됩니다."
            )

        if extra_lines:
            extra_policy_section = "\n\n■ 이월 및 수당 정책\n" + \
                                   "\n".join(f"- {line}" for line in extra_lines)

    keywords = [name]
    if code:
        keywords.append(code)
    if "휴가" in name:
        keywords.append(name.replace(" 휴가", "").replace("휴가", ""))
    if is_paid:
        keywords.append("유급")
    keywords_str = ", ".join(set(keywords))

    details_str = "\n".join(details)

    return f"""[휴가] {name}

■ 설명
{description}

■ 상세 내용
{details_str}{extra_policy_section}

■ 관련 키워드
{keywords_str}"""


async def sync_leave_documents(
        company_id: str,
        db: Session) -> dict:
    """회사의 휴가 정책을 RAG 문서로 동기화"""

    logger.info(
        f"[leave_syncer] 동기화 시작: company={company_id}"
    )

    # 1. API 호출
    try:
        leave_types = await salary_client.get_company_leave_types(
            company_id
        )
    except Exception as e:
        logger.error(f"[leave_syncer] 휴가 종류 조회 실패: {e}")
        raise

    if not leave_types:
        logger.warning(
            f"[leave_syncer] 휴가 종류 없음: company={company_id}"
        )
        return {"synced": 0, "deleted": 0}

    try:
        policies = await salary_client.get_leave_policies(company_id)
        annual_policy = policies[0] if policies else None
    except Exception as e:
        logger.warning(f"[leave_syncer] 연차 정책 조회 실패 (무시): {e}")
        annual_policy = None

    # 2. 기존 db_sync 휴가 문서 삭제
    existing_docs = db.query(HrDocument).filter(
        HrDocument.company_id == company_id,
        HrDocument.layer == "db_sync",
        HrDocument.document_name.like("leave_%"),
        HrDocument.del_yn == "NO"
    ).all()

    deleted_count = 0
    for doc in existing_docs:
        try:
            delete_vectors(doc.id)
        except Exception as e:
            logger.error(
                f"[leave_syncer] Pinecone 삭제 실패: {doc.id}, {e}"
            )
        doc.del_yn = "YES"
        deleted_count += 1

    if deleted_count > 0:
        db.commit()
        logger.info(
            f"[leave_syncer] 기존 휴가 문서 {deleted_count}개 삭제"
        )

    # 3. 새 문서 생성
    synced_count = 0
    for leave_type in leave_types:
        code = leave_type.get("code", "UNKNOWN")
        document_name = f"leave_{code}.auto.txt"

        content = format_leave_type_to_rag(
            leave_type,
            annual_policy=annual_policy if code == "ANNUAL" else None
        )

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
            synced_count += 1
            logger.info(
                f"[leave_syncer] 업로드 완료: {document_name}"
            )
        except Exception as e:
            logger.error(
                f"[leave_syncer] 업로드 실패: {document_name}, {e}"
            )
            db.delete(new_doc)
            db.commit()

    logger.info(
        f"[leave_syncer] 동기화 완료: "
        f"synced={synced_count}, deleted={deleted_count}"
    )

    return {
        "synced": synced_count,
        "deleted": deleted_count
    }