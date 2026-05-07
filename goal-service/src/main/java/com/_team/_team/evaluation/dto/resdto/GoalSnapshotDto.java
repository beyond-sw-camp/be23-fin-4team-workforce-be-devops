package com._team._team.evaluation.dto.resdto;

import com._team._team.goal.domain.Goal;
import com._team._team.goal.domain.enums.GoalStatus;
import com._team._team.goal.domain.enums.KpiCycle;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 평가 시즌 시작 시점의 목표 단건 스냅샷.
 *
 * EvaluationResponse.goalSnapshotJson 안에 이 DTO의 목록이 JSON 배열로 직렬화되어 저장된다.
 * 목표가 나중에 수정되더라도 평가 시점의 "그때 그 값"이 보존된다.
 *
 * 새 @Entity 를 만드는 대신 JSON 필드에 캡처하는 전략이므로, 반드시 모든 필드가 nullable 허용.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GoalSnapshotDto {

    private UUID goalId;
    private String title;
    private String description;
    private GoalStatus statusAtSnapshot;
    private KpiCycle cycle;
    private BigDecimal actualValueAtSnapshot;
    private BigDecimal achievementPctAtSnapshot;
    private BigDecimal rolledAchievementPctAtSnapshot;
    private BigDecimal weightPct;
    private String gradeS;
    private String gradeA;
    private String gradeB;
    private String gradeC;

    private LocalDate startDate;
    private LocalDate endDate;

    private UUID ownerId;
    /** 스냅샷이 생성된 시점 (시즌 시작 또는 materialize 시점). */
    private String snapshotTakenAt;

    public static GoalSnapshotDto from(Goal goal, String takenAtIso) {
        if (goal == null) return null;
        return GoalSnapshotDto.builder()
                .goalId(goal.getGoalId())
                .title(goal.getTitle())
                .description(goal.getDescription())
                .statusAtSnapshot(goal.getStatus())
                .cycle(goal.getCycle())
                .weightPct(BigDecimal.valueOf(goal.getWeightPct()))
                .gradeS(goal.getGradeSCriteria())
                .gradeA(goal.getGradeACriteria())
                .gradeB(goal.getGradeBCriteria())
                .gradeC(goal.getGradeCCriteria())
                .startDate(goal.getCycleStartDate())
                .endDate(goal.getCycleEndDate())
                .ownerId(goal.getOwnerId())
                .snapshotTakenAt(takenAtIso)
                .build();
    }
}
