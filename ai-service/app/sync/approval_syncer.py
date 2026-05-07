"""결재 양식을 RAG 문서로 변환하여 Pinecone에 저장"""
import json
import logging
from sqlalchemy.orm import Session
from app.document.model import HrDocument
from app.document.service import process_document
from app.core.pinecone import delete_vectors
from app.sync import salary_client

logger = logging.getLogger(__name__)


REQUEST_TYPE_NAMES = {
    "VACATION": "휴가",
    "ATTENDANCE": "근태",
    "HR": "인사",
    "BUSINESS_TRIP": "출장",
    "GENERAL": "일반",
    "OFFICIAL": "공문",
}

# 카테고리별 추가 키워드 (검색 정확도 향상)
CATEGORY_KEYWORDS = {
    "VACATION": ["휴가", "연차", "휴직"],
    "ATTENDANCE": ["근태", "근무", "연장근무", "야근", "출퇴근"],
    "HR": ["인사", "사직", "퇴사", "근로계약", "수당"],
    "BUSINESS_TRIP": ["출장", "국내출장", "해외출장"],
    "GENERAL": ["기안", "보고서"],
    "OFFICIAL": ["공문"],
}


# ============================================
# ApprovalDocument
# ============================================

def format_approval_document_to_rag(document: dict) -> str:
    """결재 양식 하나를 RAG 문서로 변환"""
    document_name = document.get("documentName", "결재 양식")
    request_type = document.get("requestType", "GENERAL")
    request_type_name = REQUEST_TYPE_NAMES.get(request_type, "기타")
    is_active = document.get("isActiveYn") == "Y"
    is_calendar_visible = document.get("isCalendarVisibleYn") == "Y"
    calendar_display_name = document.get("calendarDisplayName")

    # formSchema 파싱하여 입력 필드 추출
    form_schema_str = document.get("formSchema", "")
    fields = _parse_form_fields(form_schema_str)

    # 입력 항목 섹션
    if fields:
        field_lines = []
        for field in fields:
            label = field.get("label", "")
            required = field.get("required", False)
            field_type = field.get("type", "text")

            # 사용자에게 보일 만한 정보만
            if label:
                marker = " (필수)" if required else ""
                field_lines.append(f"- {label}{marker}")
        fields_text = "\n".join(field_lines) if field_lines else "(입력 항목 없음)"
    else:
        fields_text = "(입력 항목 없음)"

    # 캘린더 연동 안내
    calendar_section = ""
    if is_calendar_visible and calendar_display_name:
        calendar_section = f"\n\n■ 캘린더 연동\n신청 시 캘린더에 '{calendar_display_name}' 일정으로 자동 표시됩니다."

    # 키워드 생성
    base_keywords = [
        document_name,
        f"{document_name} 양식",
        f"{document_name} 신청",
        request_type_name,
        "결재", "결재 양식", "결재 신청", "양식", "신청서"
    ]
    base_keywords.extend(CATEGORY_KEYWORDS.get(request_type, []))
    keywords_str = ", ".join(set(base_keywords))

    return f"""[결재] {document_name}

■ 설명
{request_type_name} 카테고리의 결재 양식입니다. 직원이 {document_name}을(를) 작성하여 결재를 요청할 때 사용합니다.

■ 양식 정보
- 양식명: {document_name}
- 카테고리: {request_type_name}

■ 입력 항목
{fields_text}{calendar_section}

■ 관련 키워드
{keywords_str}

■ 관련 메뉴
관련 메뉴: /app/salary/pay-grade-table
화면명: 결재 요청 작성"""


def _parse_form_fields(form_schema_str: str) -> list:
    """formSchema JSON에서 fields 배열 추출"""
    if not form_schema_str:
        return []

    try:
        schema = json.loads(form_schema_str)
        fields = schema.get("fields", [])

        # hidden 필드는 사용자에게 보일 필요 없음 (제외)
        visible_fields = [
            f for f in fields
            if f.get("type") != "hidden"
        ]

        return visible_fields
    except (json.JSONDecodeError, AttributeError) as e:
        logger.warning(f"[approval_syncer] formSchema 파싱 실패: {e}")
        return []


# ============================================
# 통합 동기화
# ============================================

async def sync_approval_documents(
        company_id: str,
        db: Session) -> dict:
    """
    회사의 결재 양식을 RAG 문서로 동기화.

    동기화 대상:
    - ApprovalDocument (활성화된 양식만)
    """
    logger.info(
        f"[approval_syncer] 동기화 시작: company={company_id}"
    )

    # 1. approval-service에서 활성 양식 목록 조회
    try:
        documents = await salary_client.get_approval_documents(company_id)
    except Exception as e:
        logger.error(f"[approval_syncer] 결재 양식 조회 실패: {e}")
        documents = []

    # 2. 기존 db_sync approval 문서 삭제
    existing_docs = db.query(HrDocument).filter(
        HrDocument.company_id == company_id,
        HrDocument.layer == "db_sync",
        HrDocument.document_name.like("approval_form_%"),
        HrDocument.del_yn == "NO"
    ).all()

    deleted_count = 0
    for doc in existing_docs:
        try:
            delete_vectors(doc.id)
        except Exception as e:
            logger.error(
                f"[approval_syncer] Pinecone 삭제 실패: {doc.id}, {e}"
            )
        doc.del_yn = "YES"
        deleted_count += 1

    if deleted_count > 0:
        db.commit()
        logger.info(
            f"[approval_syncer] 기존 문서 {deleted_count}개 삭제"
        )

    # 3. 신규 문서 생성 (활성 양식만)
    synced_count = 0

    for document in documents:
        # 활성화 상태만
        if document.get("isActiveYn") != "Y":
            continue

        document_id = document.get("documentId", "unknown")
        document_name = document.get("documentName", "unknown")

        # 파일명 (양식별 개별 문서)
        file_name = f"approval_form_{document_id}.auto.txt"
        content = format_approval_document_to_rag(document)

        if await _save_doc(db, company_id, file_name, content):
            synced_count += 1
            logger.info(
                f"[approval_syncer] 양식 동기화: {document_name}"
            )

    logger.info(
        f"[approval_syncer] 동기화 완료: "
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
        logger.info(f"[approval_syncer] 업로드 완료: {document_name}")
        return True
    except Exception as e:
        logger.error(
            f"[approval_syncer] 업로드 실패: {document_name}, {e}"
        )
        db.delete(new_doc)
        db.commit()
        return False