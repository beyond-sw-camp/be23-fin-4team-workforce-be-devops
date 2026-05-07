package com._team._team.goal.dto.reqdto;

import com._team._team.goal.domain.enums.GoalOwnerType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class KpiGenerateOwnerMappingReqDto {
    @NotNull
    private Integer kpiIndex;
    @NotNull
    private UUID ownerId;
    private GoalOwnerType ownerType;
}
