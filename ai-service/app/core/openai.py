import logging

from openai import OpenAI
from app.config import settings


logger = logging.getLogger(__name__)

client = OpenAI(api_key=settings.openai_api_key)


def get_embedding(text: str) -> list[float]:
    """텍스트를 임베딩 벡터로 변환"""
    response = client.embeddings.create(
        model="text-embedding-3-small",
        input=text
    )
    return response.data[0].embedding


def expand_query(question: str, conversation_history: str = "") -> str:
    """
    사용자 질문을 RAG 검색에 유리하도록 관련 키워드로 확장.
    이전 대화가 있으면 후속 질문 처리.
    """
    # 후속 질문 패턴 체크 (이전 대화가 있고 질문이 짧고 모호한 경우)
    is_followup = (
            conversation_history
            and len(question) <= 20
            and any(kw in question for kw in [
        "어디서", "어떻게", "신청", "유급", "그럼", "그건",
        "방법", "그래서", "더", "또"
    ])
    )

    if is_followup:
        try:
            response = client.chat.completions.create(
                model="gpt-4o-mini",
                messages=[
                    {
                        "role": "system",
                        "content": (
                            "당신은 HR 챗봇의 검색 쿼리 최적화 어시스턴트입니다. "
                            "사용자가 이전 대화에 이어서 짧은 후속 질문을 한 경우, "
                            "이전 대화의 주제를 결합한 검색 쿼리를 만드세요.\n\n"
                            "[규칙]\n"
                            "1. 이전 대화의 핵심 주제를 추출하세요.\n"
                            "2. 그 주제와 현재 질문의 키워드를 결합하세요.\n"
                            "3. 검색 쿼리만 출력하세요. 다른 설명 X.\n"
                            "4. 최대 5개 키워드.\n\n"
                            "[예시]\n"
                            "이전 대화:\n"
                            "Q: 결혼 휴가 며칠?\n"
                            "A: 본인 5일, 자녀 1일이며 유급으로 제공됩니다.\n"
                            "현재 질문: 어디서 신청해?\n"
                            "답변: 결혼 휴가 신청 방법\n\n"
                            "이전 대화:\n"
                            "Q: 결혼 휴가 며칠?\n"
                            "A: 본인 5일, 자녀 1일입니다.\n"
                            "현재 질문: 유급이야?\n"
                            "답변: 결혼 휴가 유급 여부\n\n"
                            "이전 대화:\n"
                            "Q: 야근 수당 얼마?\n"
                            "A: 1.5배 가산 지급됩니다.\n"
                            "현재 질문: 신청은?\n"
                            "답변: 야근 신청 방법"
                        )
                    },
                    {
                        "role": "user",
                        "content": f"[이전 대화]\n{conversation_history}\n\n[현재 질문]\n{question}\n\n[검색 쿼리]"
                    }
                ],
                temperature=0.0,
                max_tokens=50
            )
            expanded = response.choices[0].message.content.strip()

            if expanded:
                logger.info(f"[expand_query/followup] '{question}' → '{expanded}'")
                return expanded
        except Exception as e:
            logger.error(f"[expand_query/followup] 실패: {e}")

    # 기존 로직 (단발 질문 확장)
    if len(question) > 20:
        logger.info(f"[expand_query] 긴 질문 원본 유지: '{question}'")
        return question

    try:
        response = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {
                    "role": "system",
                    "content": (
                        "당신은 HR 챗봇의 검색 최적화 어시스턴트입니다. "
                        "사용자 질문에 직접 관련된 동의어만 추가하세요.\n\n"
                        "[규칙]\n"
                        "1. 원본 질문의 핵심 키워드를 그대로 유지하세요.\n"
                        "2. 동일한 의미의 동의어만 추가하세요.\n"
                        "3. 상위 개념이나 카테고리명은 절대 추가하지 마세요.\n"
                        "   - '결혼 휴가'에 '특별휴가', '경조사', '경조휴가' 추가 금지\n"
                        "   - '연차'에 '휴가', '특별휴가' 추가 금지\n"
                        "4. 다른 유사 항목을 연상시키는 단어를 추가하지 마세요.\n"
                        "   - '결혼'에 '출산', '사망' 추가 금지\n"
                        "5. 최대 5개 키워드 이내로 제한하세요.\n"
                        "6. 공백으로 구분된 키워드 목록만 반환하세요.\n\n"
                        "[좋은 예시]\n"
                        "질문: 결혼하면 휴가 얼마?\n"
                        "답변: 결혼 혼인 웨딩 일수\n\n"
                        "질문: 연차 이월 가능?\n"
                        "답변: 연차 이월 다음해 잔여\n\n"
                        "질문: 배우자 출산 휴가?\n"
                        "답변: 배우자 출산 남편 아내\n\n"
                        "[나쁜 예시 - 절대 하지 마세요]\n"
                        "질문: 결혼 휴가 며칠?\n"
                        "잘못된 답변: 결혼 휴가 특별휴가 경조사 경조휴가\n"
                        "(다른 휴가 종류까지 들어가서 검색 정확도가 떨어짐)"
                    )
                },
                {"role": "user", "content": f"질문: {question}\n답변:"}
            ],
            temperature=0.1,
            max_tokens=80
        )
        expanded = response.choices[0].message.content.strip()

        if not expanded:
            return question

        logger.info(f"[expand_query] '{question}' → '{expanded}'")
        return expanded

    except Exception as e:
        logger.error(f"[expand_query] 실패: {e}, 원본 반환")
        return question


def detect_category(question: str) -> str | None:
    """
    사용자 질문에서 HR 카테고리를 감지.

    카테고리 종류:
      - 휴가: 연차, 경조, 병가, 출산, 공가, 예비군 등
      - 급여: 월급, 지급일, 수당, 공제, 세금 등
      - 근무: 출퇴근, 야근, 유연근무, 재택, 주52시간, 지각 등
      - 결재: 결재양식, 문서번호, 승인, 반려 등
      - 목표: 목표 설정, KR, KPI 등
      - 평가: 평가 시즌, 자기평가, 동료평가 등
      - 기타: 위에 해당하지 않는 경우

    카테고리를 명확히 판단할 수 없으면 None 반환하여 전체 검색 수행.
    """
    try:
        response = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {
                    "role": "system",
                    "content": (
                        "당신은 HR 챗봇의 카테고리 분류 어시스턴트입니다. "
                        "사용자 질문을 아래 7가지 카테고리 중 하나로 분류하세요.\n\n"
                        "[중요 원칙 - 최우선]\n"
                        "사용자 질문이 '액션 가이드' 형태면 무조건 '기타'로 분류하세요.\n"
                        "액션 가이드 질문이란 정책 정보가 아닌 '어디서/어떻게 사용?'을 묻는 질문입니다.\n\n"
                        "[액션 가이드 질문 패턴 (반드시 '기타')]\n"
                        "- 'X 어디서 ~해?' / 'X 어디서 봐?'\n"
                        "- 'X 어떻게 ~해?' / 'X 어떻게 신청?' / 'X 어떻게 올려?'\n"
                        "- 'X 방법?' / 'X 어떻게 작성?'\n"
                        "- 'X 수정/변경/등록 어디서?'\n"
                        "- '~ 화면 어디?' / '~ 메뉴 어디?'\n\n"
                        "[액션 가이드 예시 - 모두 '기타']\n"
                        "- '결재 어떻게 올려?' → 기타\n"
                        "- '휴가 어떻게 신청해?' → 기타\n"
                        "- '월급 명세서 어디서 봐?' → 기타\n"
                        "- '연봉 어디서 확인해?' → 기타\n"
                        "- '야근 신청 어디서 해?' → 기타\n"
                        "- '비밀번호 어떻게 바꿔?' → 기타\n"
                        "- '계좌번호 어디서 수정?' → 기타\n"
                        "- '내 출퇴근 기록 어디서?' → 기타\n\n"
                        "[정책 정보 질문 - 해당 카테고리]\n"
                        "정책의 구체적인 값(일수, 금액, 비율 등)을 묻는 질문만 카테고리로 분류:\n"
                        "- 휴가: 연차, 반차, 결혼, 출산, 경조, 병가, 예비군, 민방위, 공가, 특별휴가\n"
                        "  예: '결혼 휴가 며칠?' / '연차 이월 가능?'\n"
                        "- 급여: 월급, 기본급, 수당, 공제, 세금, 지급일, 상여금, 4대보험\n"
                        "  예: '월급 언제 들어와?' / '식대 비과세 얼마?'\n"
                        "- 근무: 출퇴근 시간, 점심 시간, 야근 수당, 주 52시간, 유연근무 정책, 회사 휴일, 공휴일, 지각 기준, 결근 기준, 조퇴 기준\n"
                        "  예: '출근 시간 몇 시?' / '점심 시간 몇 분?' / '야근 수당 얼마?' / '주 52시간 적용돼?' / '회사 휴일 언제?' / '올해 공휴일 며칠?' / '지각 기준?' / '몇 시까지 출근?' / '결근 기준?'\n"
                        "- 결재: 결재 양식 자체에 대한 질문 (양식 종류, 양식별 내용)\n"
                        "  예: '어떤 결재 양식 있어?' / '사직서 양식에 뭐 적어?'\n"
                        "- 목표: 목표 설정, OKR, KPI, KR, 진행률\n"
                        "- 평가: 평가 시즌, 자기평가, 동료평가, 등급\n"
                        "- 기타: 위에 해당하지 않거나 액션 가이드 질문\n\n"
                        "[규칙]\n"
                        "1. 카테고리 이름 하나만 정확히 반환하세요.\n"
                        "2. 다른 설명이나 이유를 추가하지 마세요.\n"
                        "3. 명확히 판단 불가능하면 '기타'를 반환하세요.\n"
                        "4. 액션 가이드 질문은 무조건 '기타'입니다.\n\n"
                        "[종합 예시]\n"
                        "질문: 결혼 휴가 며칠? → 휴가 (정책 정보)\n"
                        "질문: 휴가 어떻게 신청? → 기타 (액션 가이드)\n"
                        "질문: 월급 언제 들어와? → 급여 (정책 정보)\n"
                        "질문: 월급 명세서 어디서? → 기타 (액션 가이드)\n"
                        "질문: 야근 수당 얼마? → 근무 (정책 정보)\n"
                        "질문: 야근 신청 어디서? → 기타 (액션 가이드)\n"
                        "질문: 출근 시간 몇 시? → 근무 (정책 정보)\n"
                        "질문: 퇴근 시간? → 근무 (정책 정보)\n"
                        "질문: 점심 시간 몇 분? → 근무 (정책 정보)\n"
                        "질문: 회사 휴일 언제? → 근무 (정책 정보)\n"
                        "질문: 올해 공휴일 며칠? → 근무 (정책 정보)\n"
                        "질문: 지각 기준? → 근무 (정책 정보)\n"
                        "질문: 지각 기준 언제야? → 근무 (정책 정보)\n"
                        "질문: 몇 시까지 가야 해? → 근무 (정책 정보)\n"
                        "질문: 결근 기준? → 근무 (정책 정보)\n"
                        "질문: 안녕 → 기타"
                    )
                },
                {"role": "user", "content": question}
            ],
            temperature=0.0,
            max_tokens=10
        )
        category = response.choices[0].message.content.strip()

        valid_categories = [
            "휴가", "급여", "근무", "결재", "목표", "평가"
        ]
        if category in valid_categories:
            logger.info(f"[detect_category] '{question}' → {category}")
            return category

        logger.info(f"[detect_category] '{question}' → 기타 (필터 없음)")
        return None

    except Exception as e:
        logger.error(f"[detect_category] 실패: {e}")
        return None