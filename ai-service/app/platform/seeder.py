"""
플랫폼 공통 문서 자동 시드 시스템.

서버 시작 시 seed_data/ 폴더의 문서들을 자동으로 Pinecone에 동기화.
- 신규 파일: 업로드
- 변경된 파일: 재업로드
- 삭제된 파일: Pinecone + DB에서 제거
"""
import logging
from pathlib import Path
from sqlalchemy.orm import Session
from app.document.model import HrDocument
from app.document.service import process_document
from app.core.pinecone import PLATFORM_COMPANY_ID, delete_vectors
from app.database import SessionLocal

logger = logging.getLogger(__name__)

SEED_DATA_DIR = Path(__file__).parent / "seed_data"


async def seed_platform_documents():
    """seed_data/ 폴더와 DB를 동기화"""

    if not SEED_DATA_DIR.exists():
        logger.warning(
            f"[platform_seeder] seed_data 폴더 없음: {SEED_DATA_DIR}"
        )
        return

    db: Session = SessionLocal()
    try:
        # seed_data 폴더의 모든 파일 읽기
        seed_files = {}
        for file_path in SEED_DATA_DIR.iterdir():
            if file_path.suffix.lower() not in ['.txt', '.md']:
                continue

            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()

            seed_files[file_path.name] = content

        logger.info(
            f"[platform_seeder] 시드 파일 {len(seed_files)}개 발견: "
            f"{list(seed_files.keys())}"
        )

        # 기존 DB의 플랫폼 문서 조회
        existing_docs = db.query(HrDocument).filter(
            HrDocument.company_id == PLATFORM_COMPANY_ID,
            HrDocument.layer == "platform",
            HrDocument.del_yn == "NO"
        ).all()

        existing_map = {doc.document_name: doc for doc in existing_docs}

        # 1. 삭제 대상: DB에 있지만 파일이 없어진 것
        for doc_name, doc in existing_map.items():
            if doc_name not in seed_files:
                logger.info(
                    f"[platform_seeder] 삭제: {doc_name}"
                )
                try:
                    delete_vectors(doc.id)
                except Exception as e:
                    logger.error(
                        f"[platform_seeder] Pinecone 삭제 실패: {e}"
                    )
                doc.del_yn = "YES"
                db.commit()

        # 2. 업로드/업데이트 대상
        for file_name, content in seed_files.items():
            existing = existing_map.get(file_name)

            # 기존 있고 내용 동일 → 스킵
            if existing and existing.content == content:
                logger.info(
                    f"[platform_seeder] 변경 없음: {file_name}"
                )
                continue

            # 기존 있고 내용 변경 → 구 버전 삭제
            if existing:
                logger.info(
                    f"[platform_seeder] 업데이트: {file_name}"
                )
                try:
                    delete_vectors(existing.id)
                except Exception as e:
                    logger.error(
                        f"[platform_seeder] Pinecone 삭제 실패: {e}"
                    )
                existing.del_yn = "YES"
                db.commit()

            # 신규 또는 업데이트 후 재삽입
            logger.info(
                f"[platform_seeder] 신규 삽입: {file_name}"
            )

            new_doc = HrDocument(
                company_id=PLATFORM_COMPANY_ID,
                document_name=file_name,
                content=content,
                layer="platform"
            )
            db.add(new_doc)
            db.commit()
            db.refresh(new_doc)

            try:
                await process_document(
                    new_doc.id,
                    PLATFORM_COMPANY_ID,
                    file_name,
                    content,
                    layer="platform"
                )
            except Exception as e:
                logger.error(
                    f"[platform_seeder] 처리 실패: {file_name}, {e}"
                )
                db.delete(new_doc)
                db.commit()

        logger.info("[platform_seeder] 시드 완료")

    finally:
        db.close()

