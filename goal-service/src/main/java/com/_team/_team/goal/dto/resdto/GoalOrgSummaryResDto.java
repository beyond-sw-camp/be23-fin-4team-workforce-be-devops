package com._team._team.goal.dto.resdto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class GoalOrgSummaryResDto {
    private UUID orgId;
    private long goalCount;
    private long draftCount;
    private long activeCount;
    private long completedCount;
    private BigDecimal avgRolledAchievementPct;
}
