package com._team._team.evaluation.dto.reqdto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.util.List;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GroupCreateReqDto {
    @NotBlank private String name;
    private List<String> evaluationTypes;
    private List<UUID> targetMemberIds;
    private UUID designId;
}
