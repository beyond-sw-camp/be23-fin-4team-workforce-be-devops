"""Kafka Consumer - 백그라운드 스레드로 RAG sync 이벤트 수신"""
import json
import logging
import asyncio
import threading
from kafka import KafkaConsumer
from app.config import settings
from app.database import SessionLocal
from app.sync.leave_syncer import sync_leave_documents
from app.sync.attendance_syncer import sync_attendance_documents
from app.sync.salary_syncer import sync_salary_documents
from app.sync.approval_syncer import sync_approval_documents

logger = logging.getLogger(__name__)

TOPICS = [
    "rag.sync.leave",
    "rag.sync.attendance",
    "rag.sync.salary",
    "rag.sync.approval",
]


def _process_event(event: dict):
    topic = event.get("_topic")
    company_id = event.get("companyId")
    action = event.get("action", "UNKNOWN")
    resource_type = event.get("resourceType", "UNKNOWN")
    event_id = event.get("eventId", "-")

    if not company_id:
        logger.warning(
            f"[kafka_consumer] companyId 누락, 스킵: {event}"
        )
        return

    logger.info(
        f"[kafka_consumer] 이벤트 수신: "
        f"topic={topic}, eventId={event_id}, "
        f"company={company_id}, action={action}, resource={resource_type}"
    )

    if topic == "rag.sync.leave":
        asyncio.run(_handle_leave_sync(company_id))
    elif topic == "rag.sync.attendance":
        asyncio.run(_handle_attendance_sync(company_id))
    elif topic == "rag.sync.salary":
        asyncio.run(_handle_salary_sync(company_id))
    elif topic == "rag.sync.approval":
        asyncio.run(_handle_approval_sync(company_id))
    else:
        logger.warning(
            f"[kafka_consumer] 핸들러 없음: topic={topic}"
        )


async def _handle_leave_sync(company_id: str):
    db = SessionLocal()
    try:
        result = await sync_leave_documents(company_id, db)
        logger.info(
            f"[kafka_consumer] 휴가 동기화 완료: "
            f"company={company_id}, result={result}"
        )
    except Exception as e:
        logger.error(
            f"[kafka_consumer] 휴가 동기화 실패: "
            f"company={company_id}, {e}"
        )
    finally:
        db.close()


async def _handle_attendance_sync(company_id: str):
    db = SessionLocal()
    try:
        result = await sync_attendance_documents(company_id, db)
        logger.info(
            f"[kafka_consumer] 근무/근태 동기화 완료: "
            f"company={company_id}, result={result}"
        )
    except Exception as e:
        logger.error(
            f"[kafka_consumer] 근무/근태 동기화 실패: "
            f"company={company_id}, {e}"
        )
    finally:
        db.close()


async def _handle_salary_sync(company_id: str):
    db = SessionLocal()
    try:
        result = await sync_salary_documents(company_id, db)
        logger.info(
            f"[kafka_consumer] 급여 동기화 완료: "
            f"company={company_id}, result={result}"
        )
    except Exception as e:
        logger.error(
            f"[kafka_consumer] 급여 동기화 실패: "
            f"company={company_id}, {e}"
        )
    finally:
        db.close()


async def _handle_approval_sync(company_id: str):
    db = SessionLocal()
    try:
        result = await sync_approval_documents(company_id, db)
        logger.info(
            f"[kafka_consumer] 결재 동기화 완료: "
            f"company={company_id}, result={result}"
        )
    except Exception as e:
        logger.error(
            f"[kafka_consumer] 결재 동기화 실패: "
            f"company={company_id}, {e}"
        )
    finally:
        db.close()


def _consume_loop():
    try:
        consumer = KafkaConsumer(
            *TOPICS,
            bootstrap_servers=settings.kafka_bootstrap_servers,
            group_id="ai-service-rag-sync",
            auto_offset_reset="latest",
            enable_auto_commit=True,
            value_deserializer=lambda x: json.loads(x.decode("utf-8")),
        )

        logger.info(
            f"[kafka_consumer] 구독 시작: topics={TOPICS}"
        )

        for message in consumer:
            try:
                event = message.value
                event["_topic"] = message.topic
                _process_event(event)
            except Exception as e:
                logger.error(
                    f"[kafka_consumer] 이벤트 처리 실패: {e}",
                    exc_info=True
                )
    except Exception as e:
        logger.error(
            f"[kafka_consumer] Consumer 실패: {e}",
            exc_info=True
        )


def start_consumer_thread():
    thread = threading.Thread(
        target=_consume_loop,
        daemon=True,
        name="kafka-consumer-rag-sync"
    )
    thread.start()
    logger.info(
        "[kafka_consumer] 백그라운드 스레드 시작 완료"
    )