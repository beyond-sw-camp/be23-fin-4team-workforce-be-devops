package com._team._team.evaluation.dto.reqdto;

import lombok.*;
import java.util.List;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GroupUpdateReqDto {
    private String name;
    private List<String> evaluationTypes;
    private List<UUID> targetMemberIds;
    private UUID designId;
}
