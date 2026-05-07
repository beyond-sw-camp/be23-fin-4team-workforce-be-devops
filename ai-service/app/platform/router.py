from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from typing import List
from app.database import get_db
from app.platform import service
from app.platform.seeder import seed_platform_documents
from app.document.schema import DocumentListRes

router = APIRouter(prefix="/ai/admin/platform", tags=["platform"])


@router.get("/documents", response_model=List[DocumentListRes])
async def list_platform_documents(
        db: Session = Depends(get_db)):
    """
    플랫폼 공통 문서 목록 조회.
    플랫폼 문서는 seed_data/ 폴더에서 자동 관리됩니다.
    """
    return service.get_platform_documents(db)


@router.post("/documents/reseed")
async def manual_reseed():
    """
    플랫폼 문서 수동 재동기화.
    서버 재시작 없이 seed_data/ 변경사항을 반영할 때 사용.
    """
    await seed_platform_documents()
    return {"message": "재동기화 완료"}