from sqlalchemy.orm import Session
from app.document.model import HrDocument
from app.core.pinecone import PLATFORM_COMPANY_ID


def get_platform_documents(db: Session) -> list[HrDocument]:
    """플랫폼 공통 문서 목록"""
    return db.query(HrDocument).filter(
        HrDocument.company_id == PLATFORM_COMPANY_ID,
        HrDocument.layer == "platform",
        HrDocument.del_yn == "NO"
    ).order_by(HrDocument.created_at.desc()).all()