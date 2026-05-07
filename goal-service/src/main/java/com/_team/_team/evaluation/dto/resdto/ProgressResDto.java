package com._team._team.evaluation.dto.resdto;

import lombok.*;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ProgressResDto {
    private UUID memberId;
    private String memberName;
    private String teamName;
    private String status;
    private String lastAccessedAt;
    private String lastRemindedAt;
}
