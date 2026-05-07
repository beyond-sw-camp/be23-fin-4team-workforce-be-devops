package com._team._team.evaluation.dto.reqdto;

import com._team._team.goal.domain.enums.Grade;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 자기평가 입력 — 항목별 등급 + 코멘트.
 *
 *  items[].criteriaId : GoalSnapshot 의 CriteriaEntry.criteriaId
 *  items[].grade      : S/A/B/C
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelfAnswersReqDto {

    @Builder.Default
    private List<ItemAnswer> items = new ArrayList<>();

    private String overallComment;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemAnswer {
        @NotNull
        private UUID criteriaId;
        @NotNull
        private Grade grade;
        private String comment;
        private String evidenceUrl;
    }
}
