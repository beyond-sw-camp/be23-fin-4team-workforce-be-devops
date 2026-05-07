package com._team._team.chat.dto.reqdto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatReqDto {

    private String question;       // 액션 흐름에서는 비어있을 수 있음
    private String sessionId;      // 액션 세션 진행 중일 때 클라이언트가 보냄
    private String action;         // 버튼 클릭 시 (go_to_form, cancel 등)
}