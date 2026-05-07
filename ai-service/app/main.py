from fastapi import FastAPI
from contextlib import asynccontextmanager
from app.document.router import router as document_router
from app.rag.router import router as rag_router
from app.platform.router import router as platform_router
from app.sync.router import router as sync_router
from app.platform.seeder import seed_platform_documents
from app.sync.kafka_consumer import start_consumer_thread
from app.database import Base, engine
from app.config import settings
import py_eureka_client.eureka_client as eureka_client
from app.transcribe.router import router as transcribe_router #
import logging
from app.action.router import router as action_router
from app.calendar_action.router import router as calendar_action_router

logger = logging.getLogger(__name__)

Base.metadata.create_all(bind=engine)

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Eureka (K8s에서는 비활성화)
    if settings.eureka_enabled and settings.eureka_server_url:
        try:
            await eureka_client.init_async(
                eureka_server=settings.eureka_server_url,
                app_name="ai-service",
                instance_port=8090,
                instance_host="localhost"
            )
            logger.info("[startup] Eureka 등록 완료")
        except Exception as e:
            logger.warning(f"[startup] Eureka 등록 실패 (무시): {e}")
    else:
        logger.info("[startup] Eureka 비활성화 (K8s Service DNS 사용)")

    # 플랫폼 시드
    try:
        logger.info("[startup] 플랫폼 시드 시작")
        await seed_platform_documents()
        logger.info("[startup] 플랫폼 시드 완료")
    except Exception as e:
        logger.error(f"[startup] 플랫폼 시드 실패: {e}")

    # Kafka Consumer 백그라운드 실행
    try:
        start_consumer_thread()
    except Exception as e:
        logger.error(f"[startup] Kafka Consumer 시작 실패: {e}")

    yield

    if settings.eureka_enabled:
        try:
            await eureka_client.stop_async()
            logger.info("[shutdown] Eureka 종료 완료")
        except Exception as e:
            logger.warning(f"[shutdown] Eureka 종료 실패 (무시): {e}")


app = FastAPI(
    title="AI Service",
    description="HR AI 서비스",
    version="1.0.0",
    lifespan=lifespan
)

app.include_router(document_router, prefix="/ai")
app.include_router(rag_router, prefix="/ai")
app.include_router(platform_router, prefix="/ai")
app.include_router(sync_router, prefix="/ai")
app.include_router(transcribe_router, prefix="/ai")
app.include_router(action_router, prefix="/ai")
app.include_router(calendar_action_router, prefix="/ai")

@app.get("/health")
def health_check():
    return {"status": "ok"}