"""캘린더 액션 세션 Redis 저장소"""
import json
import logging
import uuid
from typing import Optional

import redis

from app.config import settings
from app.calendar_action.schema import CalendarActionState

logger = logging.getLogger(__name__)

# 결재 액션과 분리된 DB 사용 (DB 6)
_redis_client = redis.Redis(
    host=settings.redis_host,
    port=settings.redis_port,
    db=6,
    decode_responses=True
)

KEY_PREFIX = "chatbot:calendar_action:state"
TTL_SECONDS = 1800  # 30분


def create_session_id() -> str:
    """캘린더 세션 ID 생성 — cal- prefix"""
    return f"cal-{uuid.uuid4()}"


def _make_key(session_id: str) -> str:
    return f"{KEY_PREFIX}:{session_id}"


def save_state(state: CalendarActionState) -> None:
    """상태 저장"""
    key = _make_key(state.session_id)
    value = state.model_dump_json()
    _redis_client.setex(key, TTL_SECONDS, value)
    logger.info(
        f"[calendar_state] 저장: session={state.session_id}, "
        f"slots={len(state.slots)}개"
    )


def load_state(session_id: str) -> Optional[CalendarActionState]:
    """상태 로드"""
    key = _make_key(session_id)
    value = _redis_client.get(key)
    if not value:
        return None

    try:
        data = json.loads(value)
        loaded = CalendarActionState(**data)
        logger.info(
            f"[calendar_state] 로드: session={session_id}, "
            f"slots={len(loaded.slots)}개"
        )
        return loaded
    except Exception as e:
        logger.error(f"[calendar_state] 로드 실패: {e}")
        return None


def clear_state(session_id: str) -> None:
    """상태 삭제"""
    key = _make_key(session_id)
    _redis_client.delete(key)
    logger.info(f"[calendar_state] 삭제: session={session_id}")