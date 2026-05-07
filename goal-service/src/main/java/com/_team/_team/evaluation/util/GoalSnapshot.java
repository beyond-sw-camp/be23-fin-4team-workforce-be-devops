package com._team._team.evaluation.util;

import com._team._team.goal.domain.Goal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * EvaluationResponse.goalSnapshotJson 의 직렬화 스키마 (v2 단순화).
 *
 *  시즌 ACTIVE 전이 시점에 Goal 의 불변 사본을 만들어 기록.
 *  v1 의 항목별 GradeCriteria 배열은 폐기 — 각 Goal 이 직접 등급 기준 4개 보유.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalSnapshot {

    private List<GoalEntry> goals;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GoalEntry {
        private UUID goalId;
        private String title;
        private String description;
        private int weightPct;
        private UUID objectiveGoalId;
        private String objectiveTitle;
        private String gradeS;
        private String gradeA;
        private String gradeB;
        private String gradeC;
    }

    public static GoalSnapshot of(List<Goal> goals, java.util.Map<UUID, Goal> objectiveMap) {
        return GoalSnapshot.builder()
                .goals(goals.stream().map(goal -> goalToEntry(goal, objectiveMap.get(goal.getAlignedOrgGoalId()))).collect(Collectors.toList()))
                .build();
    }

    private static GoalEntry goalToEntry(Goal g, Goal objective) {
        return GoalEntry.builder()
                .goalId(g.getGoalId())
                .title(g.getTitle())
                .description(g.getDescription())
                .weightPct(g.getWeightPct())
                .objectiveGoalId(objective != null ? objective.getGoalId() : null)
                .objectiveTitle(objective != null ? objective.getTitle() : null)
                .gradeS(g.getGradeSCriteria())
                .gradeA(g.getGradeACriteria())
                .gradeB(g.getGradeBCriteria())
                .gradeC(g.getGradeCCriteria())
                .build();
    }
}
