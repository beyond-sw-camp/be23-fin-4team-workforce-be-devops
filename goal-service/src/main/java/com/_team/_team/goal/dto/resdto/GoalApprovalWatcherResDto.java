package com._team._team.goal.dto.resdto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class GoalApprovalWatcherResDto {
    private UUID memberId;

    public static GoalApprovalWatcherResDto from(UUID memberId) {
        return GoalApprovalWatcherResDto.builder()
                .memberId(memberId)
                .build();
    }
}
