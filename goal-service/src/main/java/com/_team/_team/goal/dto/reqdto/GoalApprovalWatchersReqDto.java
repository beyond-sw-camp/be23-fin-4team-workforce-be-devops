package com._team._team.goal.dto.reqdto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class GoalApprovalWatchersReqDto {
    @NotEmpty
    private List<@NotNull UUID> watcherIds;
}
