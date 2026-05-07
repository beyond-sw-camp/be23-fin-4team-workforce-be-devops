package com._team._team.evaluation.dto.resdto;

import com._team._team.evaluation.domain.enums.EvalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluatorMappingResDto {
    private UUID targetMemberId;
    private UUID evaluatorId;
    private EvalType evaluationType;
    private String targetMemberProfileUrl;
    private String evaluatorProfileUrl;
}

