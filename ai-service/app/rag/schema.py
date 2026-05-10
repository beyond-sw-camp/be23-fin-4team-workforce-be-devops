from pydantic import BaseModel
from typing import List

class RagSearchRequest(BaseModel):
    question: str
    conversation_history: str = ""
    is_hr_admin: bool = False    # ⭐ 추가

class RagSearchResponse(BaseModel):
    context: str
    sources: List[str] = []