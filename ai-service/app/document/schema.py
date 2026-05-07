from pydantic import BaseModel
from datetime import datetime


class DocumentUploadRes(BaseModel):
    """문서 업로드 응답 DTO"""
    id: str
    company_id: str
    document_name: str
    layer: str
    created_at: datetime

    class Config:
        from_attributes = True


class DocumentListRes(BaseModel):
    """문서 목록 조회 응답 DTO"""
    id: str
    company_id: str
    document_name: str
    layer: str
    created_at: datetime

    class Config:
        from_attributes = True