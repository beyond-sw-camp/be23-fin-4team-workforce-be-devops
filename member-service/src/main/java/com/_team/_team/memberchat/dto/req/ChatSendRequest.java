package com._team._team.memberchat.dto.req;

import com._team._team.memberchat.domain.enums.MessageType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * STOMP/REST 공통 전송 요청 DTO.
 * - sender 관련 필드는 의도적으로 제거. 서버가 Principal 에서 확정.
 */
public record ChatSendRequest(
        @NotNull MessageType type,
        @Size(min = 1, max = 4000) String content,
        @Size(max = 64) String clientMessageId,
        Long replyToId
) {}
