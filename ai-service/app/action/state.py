"""챗봇 액션 슬롯 상태 관리 (Redis DB 5)"""
import logging
import uuid
from typing import Optional
import redis
from app.config import settings
from app.action.schema import ActionState

logger = logging.getLogger(__name__)


# Redis 연결 (my-redis:6379, DB 5는 챗봇 액션 전용)
_redis_client = redis.Redis(
    host=settings.redis_host,
    port=settings.redis_port,
    db=5,
    decode_responses=True
)

# TTL 30분
STATE_TTL_SECONDS = 1800

# 키 prefix
KEY_PREFIX = "chatbot:action:state"


def _build_key(session_id: str) -> str:
    return f"{KEY_PREFIX}:{session_id}"


def create_session_id() -> str:
    return str(uuid.uuid4())


def save_state(state: ActionState) -> None:
    key = _build_key(state.session_id)
    value = state.model_dump_json()

    try:
        _redis_client.setex(key, STATE_TTL_SECONDS, value)
        logger.info(
            f"[state] 저장: session={state.session_id}, "
            f"document={state.document_name}, slots={len(state.slots)}개"
        )
    except Exception as e:
        logger.error(f"[state] 저장 실패: {e}")
        raise


def load_state(session_id: str) -> Optional[ActionState]:
    key = _build_key(session_id)

    try:
        value = _redis_client.get(key)
        if not value:
            logger.info(f"[state] 세션 없음: {session_id}")
            return None

        state = ActionState.model_validate_json(value)
        logger.info(
            f"[state] 로드: session={session_id}, "
            f"document={state.document_name}, slots={len(state.slots)}개"
        )
        return state

    except Exception as e:
        logger.error(f"[state] 로드 실패: {e}")
        return None


def clear_state(session_id: str) -> None:
    key = _build_key(session_id)

    try:
        _redis_client.delete(key)
        logger.info(f"[state] 삭제: session={session_id}")
    except Exception as e:
        logger.error(f"[state] 삭제 실패: {e}")


def update_slots(session_id: str, new_slots: dict) -> Optional[ActionState]:
    state = load_state(session_id)
    if not state:
        return None

    state.slots.update(new_slots)
    save_state(state)
    return state