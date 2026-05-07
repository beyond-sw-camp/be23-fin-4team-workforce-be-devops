from pydantic import BaseModel
from typing import Optional


class TranscribeResponse(BaseModel):
    """STT(원본) + GPT 정리(회의록) 결과를 함께 내려주는 응답 DTO."""
    transcript: str          # Whisper로 받아쓴 원본 텍스트
    summary: str             # GPT가 회의록 형식으로 정리한 마크다운 텍스트
    language: Optional[str] = None


class SummarizeRequest(BaseModel):
    """이미 받아쓴 텍스트를 다시 회의록 형식으로 정리하고 싶을 때 사용."""
    transcript: str


class SummarizeResponse(BaseModel):
    summary: str