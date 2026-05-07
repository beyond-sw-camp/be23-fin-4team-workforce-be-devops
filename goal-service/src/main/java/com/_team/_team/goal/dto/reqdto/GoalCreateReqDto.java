package com._team._team.goal.dto.reqdto;

import com._team._team.goal.domain.enums.GoalOwnerType;
import com._team._team.goal.domain.enums.KpiCycle;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalCreateReqDto {

    @NotNull
    private GoalOwnerType ownerType;

    @NotNull
    private UUID ownerId;

    private UUID alignedOrgGoalId;

    @NotBlank
    @Size(max = 300)
    private String title;

    @NotBlank
    private String description;

    @NotNull
    private KpiCycle cycle;

    @NotNull
    private LocalDate cycleStartDate;

    @NotNull
    private LocalDate cycleEndDate;

    @Min(0)
    @Max(100)
    private int weightPct;

    private Boolean requireApproval;
    private Boolean activateImmediately;

    @Builder.Default
    private List<UUID> visibleTeamIds = new ArrayList<>();

    @Builder.Default
    private List<UUID> participantMemberIds = new ArrayList<>();

    // Required only for MEMBER goals. ORGANIZATION goals may leave these empty.
    private String gradeS;
    private String gradeA;
    private String gradeB;
    private String gradeC;
}
