package com._team._team.goal.dto.reqdto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 목표 부분 수정 (DRAFT 상태에서만 허용).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalUpdateReqDto {

    private UUID ownerId;

    @Size(max = 300)
    private String title;

    private String description;

    @Min(0) @Max(100)
    private Integer weightPct;

    private UUID alignedOrgGoalId;

    private List<UUID> visibleTeamIds;

    private List<UUID> participantMemberIds;

    // v2: 등급 기준 4개 (null 이면 미변경)
    private String gradeS;
    private String gradeA;
    private String gradeB;
    private String gradeC;
}
