package com._team._team.evaluation.dto.reqdto;

import com._team._team.evaluation.domain.enums.EvalType;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EvaluatorMapUpdateReqDto {
    private List<EvaluatorMapItemReqDto> evaluatorMaps;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EvaluatorMapItemReqDto {
        private UUID targetMemberId;
        private UUID evaluatorId;
        private EvalType evaluationType;
    }
}
