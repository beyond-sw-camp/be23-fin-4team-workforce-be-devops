from sqlalchemy import Column, String, Text, DateTime
from sqlalchemy.sql import func
from datetime import datetime, timezone
from app.database import Base
import uuid


class HrDocument(Base):
    __tablename__ = "hr_document"

    id = Column(
        String(36),
        primary_key=True,
        default=lambda: str(uuid.uuid4())
    )
    company_id = Column(String(36), nullable=False, index=True)
    document_name = Column(String(255), nullable=False)
    content = Column(Text, nullable=False)

    layer = Column(String(30), nullable=False, default="hr_uploaded")

    created_at = Column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),   # Python 쪽 기본값 추가
        server_default=func.now()
    )
    updated_at = Column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),   # Python 쪽 기본값 추가
        server_default=func.now(),
        onupdate=func.now()
    )
    del_yn = Column(String(3), nullable=False, default="NO")