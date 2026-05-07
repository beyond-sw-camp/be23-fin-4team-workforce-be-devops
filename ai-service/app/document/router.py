from fastapi import APIRouter, Depends, UploadFile, File, Header, Query
from sqlalchemy.orm import Session
from typing import Optional, List
from app.database import get_db
from app.document import service
from app.document.schema import DocumentUploadRes, DocumentListRes

router = APIRouter(prefix="/documents", tags=["documents"])


@router.post("/upload", response_model=DocumentUploadRes)
async def upload(
        file: UploadFile = File(...),
        x_user_company_id: str = Header(..., alias="X-User-CompanyId"),
        db: Session = Depends(get_db)):
    """HR 관리자가 업로드하는 회사 고유 정책 문서"""
    document = await service.upload_document(
        file, x_user_company_id, db, layer="hr_uploaded")
    return document


@router.get("", response_model=List[DocumentListRes])
async def list_documents(
        x_user_company_id: str = Header(..., alias="X-User-CompanyId"),
        layer: Optional[str] = Query(
            None,
            description="platform / db_sync / hr_uploaded / all (기본: hr_uploaded)"
        ),
        db: Session = Depends(get_db)):
    """문서 목록 조회"""
    return service.get_documents(x_user_company_id, db, layer)


@router.delete("/{document_id}")
async def delete(
        document_id: str,
        x_user_company_id: str = Header(..., alias="X-User-CompanyId"),
        db: Session = Depends(get_db)):
    """문서 삭제 (플랫폼 문서는 삭제 불가)"""
    service.delete_document(document_id, x_user_company_id, db)
    return {"message": "삭제 완료"}