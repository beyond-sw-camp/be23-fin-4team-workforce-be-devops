package com._team._team.chat.service;

import com._team._team.chat.domain.ChatHistory;
import com._team._team.chat.dto.resdto.*;
import com._team._team.chat.dto.reqdto.*;
import com._team._team.chat.repository.ChatHistoryRepository;
import com._team._team.dto.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class ChatService {

    private final RestTemplate restTemplate;
    private final ChatHistoryRepository chatHistoryRepository;
    private final SemanticMemoryService semanticMemoryService;
    private final ObjectMapper objectMapper;

    // 캐시 우회 대상 응답 타입 (ACTION/CALENDAR_ACTION 응답)
    private static final Set<String> NON_CACHEABLE_TYPES = Set.of(
            "ask", "confirm", "redirect_to_form", "cancelled", "error"
    );

    // 캐시 우회 키워드 패턴 (ACTION 흐름 의도)
    private static final String ACTION_KEYWORD_PATTERN =
            ".*(작성|올려|올리기|써줘|신청서|일정|등록해줘|잡아줘|미팅|회의|예약|스케줄).*";

    @Value("${n8n.webhook.url}")
    private String n8nWebhookUrl;

    @Autowired
    public ChatService(RestTemplate restTemplate, ChatHistoryRepository chatHistoryRepository, SemanticMemoryService semanticMemoryService, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.chatHistoryRepository = chatHistoryRepository;
        this.semanticMemoryService = semanticMemoryService;
        this.objectMapper = objectMapper;
    }

    // 챗봇 질문
    public ChatResDto chat(UUID memberId, UUID companyId,
                           UUID memberPositionId, String authorization,
                           String isHrAdminHeader,
                           ChatReqDto reqDto) {
        try {
            // 1.5. 액션 흐름 여부 판단 (캐시 우회 + 이력 제외에 공통 사용)
            boolean isActionFlow = reqDto.getSessionId() != null || reqDto.getAction() != null;
            boolean hasActionKeyword = reqDto.getQuestion() != null
                    && reqDto.getQuestion().matches(ACTION_KEYWORD_PATTERN);
            boolean isCacheable = reqDto.getQuestion() != null
                    && !reqDto.getQuestion().isBlank()
                    && !isActionFlow
                    && !hasActionKeyword;

            // 1. SemanticMemory KNN 검색 (캐시 가능한 경우만)
            if (isCacheable) {
                String cachedAnswer = semanticMemoryService.findBotReplyWithKnn(
                        companyId.toString(),
                        memberId.toString(),
                        reqDto.getQuestion());

                if (cachedAnswer != null) {
                    log.info("SemanticMemory 캐시 히트");
                    chatHistoryRepository.save(ChatHistory.builder()
                            .memberId(memberId)
                            .companyId(companyId)
                            .question(reqDto.getQuestion())
                            .answer(cachedAnswer)
                            .build());
                    return ChatResDto.builder().answer(cachedAnswer).build();
                }
            }

            // 2. 최근 대화 이력 조회 (액션 흐름 진행 중이면 제외)
            String conversationHistory;
            if (isActionFlow || hasActionKeyword) {
                conversationHistory = "";
            } else {
                conversationHistory = buildConversationHistory(memberId, companyId);
            }

            // 3. n8n 호출
            ChatN8nReqDto n8nReqDto = ChatN8nReqDto.builder()
                    .question(reqDto.getQuestion())
                    .memberId(memberId.toString())
                    .companyId(companyId.toString())
                    .memberPositionId(memberPositionId.toString())
                    .accessToken(authorization.replace("Bearer ", ""))
                    .conversationHistory(conversationHistory)
                    .sessionId(reqDto.getSessionId())
                    .action(reqDto.getAction())
                    .isHrAdmin("YES".equalsIgnoreCase(isHrAdminHeader))
                    .build();

            ResponseEntity<String> response =
                    restTemplate.postForEntity(
                            n8nWebhookUrl, n8nReqDto, String.class);

            log.info("n8n raw 응답: {}", response.getBody());

            // 4. body null 체크
            if (response.getBody() == null || response.getBody().isBlank()) {
                log.error("n8n 응답 body null - status: {}", response.getStatusCode());
                throw new BusinessException(
                        HttpStatus.BAD_GATEWAY,
                        "챗봇 서비스 응답이 없습니다.");
            }

            // 5. String → Map 파싱
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> bodyMap = objectMapper.readValue(
                    response.getBody(), Map.class);

            String answer = (String) bodyMap.get("answer");

            // 6. answer null 체크
            if (answer == null) {
                log.error("n8n 응답 answer 필드 null - body: {}", response.getBody());
                throw new BusinessException(
                        HttpStatus.BAD_GATEWAY,
                        "챗봇 서비스 응답 형식이 올바르지 않습니다.");
            }

            // 7. Redis Stack 저장 (캐시 가능 + 응답 타입이 일반 답변일 때만)
            String responseType = bodyMap.get("type") != null
                    ? bodyMap.get("type").toString()
                    : null;
            boolean isActionResponse = responseType != null
                    && NON_CACHEABLE_TYPES.contains(responseType);

            if (isCacheable && !isActionResponse) {
                UUID uuidKey = UUID.randomUUID();
                semanticMemoryService.saveToRedis(
                        memberId.toString(), companyId.toString(),
                        "USER", reqDto.getQuestion(), uuidKey);
                semanticMemoryService.saveToRedis(
                        memberId.toString(), companyId.toString(),
                        "BOT", answer, uuidKey);
            } else {
                log.info("캐시 저장 우회: isCacheable={}, responseType={}",
                        isCacheable, responseType);
            }

            // 8. DB 저장 (질문이 빈 문자열이면 버튼 라벨로 대체)
            String questionForHistory = reqDto.getQuestion() != null && !reqDto.getQuestion().isBlank()
                    ? reqDto.getQuestion()
                    : "[버튼: " + reqDto.getAction() + "]";

            chatHistoryRepository.save(ChatHistory.builder()
                    .memberId(memberId)
                    .companyId(companyId)
                    .question(questionForHistory)
                    .answer(answer)
                    .build());

            // 9. 응답 빌드 (액션 필드들 매핑)
            return buildChatResDto(answer, bodyMap);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("n8n 챗봇 호출 실패: {}", e.getMessage());
            throw new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "챗봇 서비스 호출 실패");
        }
    }

    // 대화 이력 조회
    @Transactional(readOnly = true)
    public List<ChatHistoryResDto> getChatHistory(UUID memberId, UUID companyId) {
        return chatHistoryRepository
                .findByMemberIdAndCompanyIdOrderByCreatedAtAsc(memberId, companyId)
                .stream()
                .map(ChatHistoryResDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 대화 이력 삭제
    public void deleteChatHistory(UUID memberId, UUID companyId) {
        chatHistoryRepository.deleteByMemberIdAndCompanyId(memberId, companyId);
    }

    /**
     * 최근 5턴 + 30분 이내 대화 이력을 LLM 컨텍스트용 문자열로 변환
     */
    private String buildConversationHistory(UUID memberId, UUID companyId) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(30);
        Pageable pageable = PageRequest.of(0, 5);

        List<ChatHistory> recent = chatHistoryRepository.findRecentHistory(
                memberId,
                companyId,
                since,
                pageable
        );

        if (recent.isEmpty()) {
            return "";
        }

        Collections.reverse(recent);

        return recent.stream()
                .map(h -> String.format("Q: %s\nA: %s", h.getQuestion(), h.getAnswer()))
                .collect(Collectors.joining("\n---\n"));
    }

    /**
     * n8n 응답 body를 ChatResDto로 변환
     * 액션 필드가 있으면 매핑, 없으면 answer만 반환
     */
    @SuppressWarnings("unchecked")
    private ChatResDto buildChatResDto(String answer, Map<String, Object> bodyMap) {
        ChatResDto.ChatResDtoBuilder builder = ChatResDto.builder().answer(answer);

        // 액션 응답 필드들 매핑 (있을 때만)
        Object type = bodyMap.get("type");
        if (type != null) builder.type(type.toString());

        Object sessionId = bodyMap.get("sessionId");
        if (sessionId != null) builder.sessionId(sessionId.toString());

        Object redirectUrl = bodyMap.get("redirectUrl");
        if (redirectUrl != null) builder.redirectUrl(redirectUrl.toString());

        Object requestId = bodyMap.get("requestId");
        if (requestId != null) builder.requestId(requestId.toString());

        Object errorCode = bodyMap.get("errorCode");
        if (errorCode != null) builder.errorCode(errorCode.toString());

        Object preview = bodyMap.get("preview");
        if (preview instanceof Map) builder.preview((Map<String, Object>) preview);

        Object actions = bodyMap.get("actions");
        if (actions instanceof List) {
            List<Map<String, Object>> actionList = (List<Map<String, Object>>) actions;
            List<ChatResDto.ActionButton> buttons = actionList.stream()
                    .map(a -> ChatResDto.ActionButton.builder()
                            .label((String) a.get("label"))
                            .value((String) a.get("value"))
                            .build())
                    .toList();
            builder.actions(buttons);
        }

        return builder.build();
    }
}