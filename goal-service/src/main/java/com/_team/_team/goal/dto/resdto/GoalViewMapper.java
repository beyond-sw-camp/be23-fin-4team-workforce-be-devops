package com._team._team.goal.dto.resdto;

import com._team._team.goal.domain.Goal;
import com._team._team.goal.domain.enums.GoalOwnerType;

public final class GoalViewMapper {

    private GoalViewMapper() {
    }

    public static GoalViewResDto toView(Goal goal) {
        if (goal.getOwnerType() == GoalOwnerType.ORGANIZATION) {
            return GoalObjectiveResDto.from(goal);
        }
        return GoalKrResDto.from(goal);
    }
}
