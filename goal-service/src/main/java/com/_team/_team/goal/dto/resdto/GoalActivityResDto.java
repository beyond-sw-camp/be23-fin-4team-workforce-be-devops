package com._team._team.goal.dto.resdto;

import com._team._team.goal.domain.GoalActivity;
import com._team._team.goal.domain.enums.GoalActivityType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class GoalActivityResDto {
    private UUID activityId;
    private GoalActivityType type;
    private UUID actorId;
    private LocalDateTime createdAt;
    private String summary;
    private String meta;

    public static GoalActivityResDto from(GoalActivity activity) {
        return GoalActivityResDto.builder()
                .activityId(activity.getActivityId())
                .type(activity.getType())
                .actorId(activity.getActorId())
                .createdAt(activity.getCreatedAt())
                .summary(activity.getSummary())
                .meta(activity.getMetaJson())
                .build();
    }
}
