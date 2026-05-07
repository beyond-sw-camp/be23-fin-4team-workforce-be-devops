package com._team._team.chat.dto.resdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatResDto {
    private String answer;
    // 액션 응답 필드들 (액션이 아닌 응답일 때 모두 null)
    private String type;                      // ask | confirm | redirect_to_form | cancelled | error
    private String sessionId;                  // 액션 세션 ID
    private List<ActionButton> actions;        // 사용자에게 보여줄 버튼들
    private Map<String, Object> preview;       // 미리보기 데이터
    private String redirectUrl;                // 결재 화면 이동 URL
    private String requestId;                  // 결재 요청 ID (안 씀, 호환용)
    private String errorCode;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ActionButton {
        private String label;
        private String value;
    }
}
