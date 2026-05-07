"""
AI 받아쓰기 / 회의록 정리 라우터.

게이트웨이 라우팅 규칙: /ai/** → ai-service
실제 엔드포인트:
  POST /ai/transcribe          : 오디오 파일 → 원문 + 회의록
  POST /ai/transcribe/summary  : 이미 가진 텍스트만 회의록으로 정리
"""
from fastapi import APIRouter, File, Form, Header, UploadFile

from app.transcribe import service
from app.transcribe.schema import (
    SummarizeRequest,
    SummarizeResponse,
    TranscribeResponse,
)

router = APIRouter(tags=["transcribe"])


@router.post("/transcribe", response_model=TranscribeResponse)
async def transcribe(
    audio: UploadFile = File(..., description="webm/m4a/mp3/wav 등 25MB 이하"),
    language: str = Form("ko"),
    # 멀티테넌시/감사로그용 헤더 (gateway가 JWT에서 추출해서 붙여줌)
    x_user_company_id: str = Header(..., alias="X-User-CompanyId"),
    x_user_uuid: str = Header(..., alias="X-User-UUID"),
):
    result = service.process_audio(audio, language=language)
    return TranscribeResponse(**result)


@router.post("/transcribe/summary", response_model=SummarizeResponse)
async def summarize_only(
    body: SummarizeRequest,
    x_user_company_id: str = Header(..., alias="X-User-CompanyId"),
    x_user_uuid: str = Header(..., alias="X-User-UUID"),
):
    summary = service.summarize_transcript(body.transcript)
    return SummarizeResponse(summary=summary)