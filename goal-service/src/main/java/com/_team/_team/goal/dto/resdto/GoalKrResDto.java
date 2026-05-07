package com._team._team.goal.dto.resdto;

import com._team._team.goal.domain.Goal;
import com._team._team.goal.domain.enums.GoalApprovalStatus;
import com._team._team.goal.domain.enums.GoalOwnerType;
import com._team._team.goal.domain.enums.GoalStatus;
import com._team._team.goal.domain.enums.KpiCycle;
import com._team._team.goal.util.CycleKeyResolver;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalKrResDto implements GoalViewResDto {
    private UUID goalId;
    private UUID companyId;
    private GoalOwnerType ownerType;
    private UUID ownerId;
    private UUID alignedOrgGoalId;
    private String title;
    private String description;
    private KpiCycle cycle;
    private LocalDate cycleStartDate;
    private LocalDate cycleEndDate;
    private String cycleKey;
    private int weightPct;
    private GoalStatus status;
    private GoalApprovalStatus goalApprovalStatus;
    private UUID approvedBy;
    private LocalDateTime approvedAt;
    private List<UUID> visibleTeamIds;
    private List<UUID> participantMemberIds;
    private String objectiveTitle;
    private String gradeS;
    private String gradeA;
    private String gradeB;
    private String gradeC;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GoalKrResDto from(Goal goal) {
        return GoalKrResDto.builder()
                .goalId(goal.getGoalId())
                .companyId(goal.getCompanyId())
                .ownerType(goal.getOwnerType())
                .ownerId(goal.getOwnerId())
                .alignedOrgGoalId(goal.getAlignedOrgGoalId())
                .title(goal.getTitle())
                .description(goal.getDescription())
                .cycle(goal.getCycle())
                .cycleStartDate(goal.getCycleStartDate())
                .cycleEndDate(goal.getCycleEndDate())
                .cycleKey(CycleKeyResolver.resolve(goal.getCycle(), goal.getCycleStartDate()))
                .weightPct(goal.getWeightPct())
                .status(goal.getStatus())
                .goalApprovalStatus(goal.getGoalApprovalStatus())
                .approvedBy(goal.getApprovedBy())
                .approvedAt(goal.getApprovedAt())
                .visibleTeamIds(goal.getVisibleTeamIds() != null ? goal.getVisibleTeamIds() : Collections.emptyList())
                .participantMemberIds(goal.getParticipantMemberIds() != null ? goal.getParticipantMemberIds() : Collections.emptyList())
                .gradeS(goal.getGradeSCriteria())
                .gradeA(goal.getGradeACriteria())
                .gradeB(goal.getGradeBCriteria())
                .gradeC(goal.getGradeCCriteria())
                .createdAt(goal.getCreatedAt())
                .updatedAt(goal.getUpdatedAt())
                .build();
    }

    public static GoalKrResDto from(Goal goal, Goal objective) {
        GoalKrResDto dto = from(goal);
        if (objective != null) {
            dto.objectiveTitle = objective.getTitle();
        }
        return dto;
    }
}
