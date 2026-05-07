package com._team._team.goal.dto.resdto;

import com._team._team.goal.domain.enums.KpiCycle;

import java.time.LocalDate;

public record GoalCycleResDto(
        KpiCycle cycle,
        LocalDate cycleStartDate,
        LocalDate cycleEndDate,
        long organizationGoalCount
) {
}
