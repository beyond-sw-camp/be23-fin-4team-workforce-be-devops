from fastapi import APIRouter, Header
from app.rag import service
from app.rag.schema import RagSearchRequest, RagSearchResponse

router = APIRouter(prefix="/rag", tags=["rag"])


@router.post("/search", response_model=RagSearchResponse)
async def search(
        request: RagSearchRequest,
        x_user_company_id: str = Header(..., alias="X-User-CompanyId")):

    # 임시 디버그
    import logging
    logger = logging.getLogger(__name__)
    logger.info(f"[search_endpoint] question={request.question!r}")
    logger.info(f"[search_endpoint] conversation_history 길이={len(request.conversation_history)}")


    result = await service.rag_search(request.question, x_user_company_id,
                                      request.conversation_history)
    return result