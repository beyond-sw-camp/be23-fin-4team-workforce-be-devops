"""
음성 → 텍스트(Whisper) → 회의록(GPT) 파이프라인.

"""
from __future__ import annotations

import io
import logging
import os
from typing import Optional

from fastapi import HTTPException, UploadFile

from app.core.openai import client  # 이미 존재하는 OpenAI 클라이언트 재사용

logger = logging.getLogger(__name__)

# Whisper API 단건 업로드 한도 (현재 25MB)
WHISPER_MAX_BYTES = 25 * 1024 * 1024

# 받아쓰기 허용 확장자 (브라우저 MediaRecorder는 보통 webm/ogg, 모바일은 m4a)
ALLOWED_AUDIO_EXTS = {
    ".webm", ".ogg", ".mp3", ".m4a", ".mp4", ".wav", ".mpga", ".mpeg",
}


# ---------------------------------------------------------------
# 1) Whisper STT
# ---------------------------------------------------------------
def transcribe_audio(file: UploadFile, language: str = "ko") -> str:
    """
    Whisper API로 음성 파일을 텍스트로 변환.
    - file: FastAPI UploadFile (multipart/form-data)
    - language: ISO-639-1 (ko, en, ja ...)
    """
    filename = file.filename or "audio.webm"
    ext = os.path.splitext(filename)[1].lower()
    if ext and ext not in ALLOWED_AUDIO_EXTS:
        raise HTTPException(
            status_code=400,
            detail=f"지원하지 않는 오디오 형식입니다: {ext}",
        )

    # 메모리에 읽되 크기 가드
    raw = file.file.read()
    if len(raw) == 0:
        raise HTTPException(status_code=400, detail="비어있는 오디오 파일입니다.")
    if len(raw) > WHISPER_MAX_BYTES:
        raise HTTPException(
            status_code=413,
            detail="Whisper API는 25MB 이하의 파일만 지원합니다. "
                   "녹음 시간을 줄이거나 청크로 나눠 업로드하세요.",
        )

    try:
        # openai SDK는 (filename, file-like) 튜플을 받습니다.
        buf = io.BytesIO(raw)
        buf.name = filename
        result = client.audio.transcriptions.create(
            model="whisper-1",
            file=buf,
            language=language,
            response_format="text",  # 단순 텍스트만 필요
        )
        # response_format="text"면 SDK가 str을 그대로 반환
        text = result if isinstance(result, str) else getattr(result, "text", "")
        text = (text or "").strip()
        if not text:
            raise HTTPException(status_code=422, detail="음성에서 텍스트를 추출하지 못했습니다.")
        return text
    except HTTPException:
        raise
    except Exception as e:  # noqa: BLE001
        logger.error(f"[transcribe_audio] Whisper 호출 실패: {e}")
        raise HTTPException(status_code=502, detail=f"STT 변환 실패: {e}")


# ---------------------------------------------------------------
# 2) GPT 회의록 정리
# ---------------------------------------------------------------
_SYSTEM_PROMPT = (
    "당신은 한국 기업의 회의록 작성 전문가입니다. "
    "주어진 회의 원문을 한국어로 정리하되, 사실에 없는 내용을 만들어내지 마세요. "
    "문서는 마크다운 형식으로 작성합니다."
)

_USER_TEMPLATE = """다음 회의 내용을 아래 형식으로 정리해줘.
빠진 항목이 있으면 해당 섹션은 "해당 없음"으로 표기해.

## 📅 회의 요약
- 한 문장으로 회의의 핵심 목적을 요약

## ✅ 주요 결정사항
- 합의/결정된 항목을 불릿으로 정리

## 📋 액션 아이템
- 형식: "- [담당자] 할 일 (기한)"
- 담당자/기한이 명시 안 됐으면 "(미정)"으로 표시

## 💬 주요 논의 내용
- 결정에 이르기까지의 핵심 논의를 3~7개 불릿으로

회의 내용:
\"\"\"{transcript}\"\"\"
"""


def summarize_transcript(transcript: str) -> str:
    """Whisper 원문을 회의록 마크다운으로 정리."""
    if not transcript or not transcript.strip():
        raise HTTPException(status_code=400, detail="요약할 텍스트가 비어있습니다.")

    try:
        resp = client.chat.completions.create(
            model="gpt-4o-mini",  # 비용/품질 균형. 운영에서 gpt-4o로 올려도 됨
            messages=[
                {"role": "system", "content": _SYSTEM_PROMPT},
                {"role": "user", "content": _USER_TEMPLATE.format(transcript=transcript)},
            ],
            temperature=0.3,
        )
        content = resp.choices[0].message.content or ""
        content = content.strip()
        if not content:
            raise HTTPException(status_code=502, detail="GPT 응답이 비어있습니다.")
        return content
    except HTTPException:
        raise
    except Exception as e:  # noqa: BLE001
        logger.error(f"[summarize_transcript] GPT 호출 실패: {e}")
        raise HTTPException(status_code=502, detail=f"회의록 정리 실패: {e}")


# ---------------------------------------------------------------
# 3) 통합 파이프라인 (라우터에서 호출)
# ---------------------------------------------------------------
def process_audio(file: UploadFile, language: Optional[str] = "ko") -> dict:
    transcript = transcribe_audio(file, language=language or "ko")
    summary = summarize_transcript(transcript)
    return {
        "transcript": transcript,
        "summary": summary,
        "language": language,
    }