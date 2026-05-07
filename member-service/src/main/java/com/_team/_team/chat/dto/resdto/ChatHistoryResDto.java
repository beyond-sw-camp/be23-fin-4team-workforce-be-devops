package com._team._team.chat.dto.resdto;

import com._team._team.chat.domain.ChatHistory;
import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatHistoryResDto {

    private UUID chatHistoryId;
    private String question;
    private String answer;

    public static ChatHistoryResDto fromEntity(ChatHistory chatHistory) {
        return ChatHistoryResDto.builder()
                .chatHistoryId(chatHistory.getChatHistoryId())
                .question(chatHistory.getQuestion())
                .answer(chatHistory.getAnswer())
                .build();
    }
}