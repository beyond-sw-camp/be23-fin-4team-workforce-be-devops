package com._team._team.chat.dto.reqdto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatN8nReqDto {

    private String question;
    private String memberId;
    private String companyId;
    private String memberPositionId;
    private String accessToken;
    private String conversationHistory;
    private String sessionId;
    private String action;

}