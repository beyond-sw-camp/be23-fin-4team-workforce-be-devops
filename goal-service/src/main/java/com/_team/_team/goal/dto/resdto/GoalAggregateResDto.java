package com._team._team.goal.dto.resdto;

import com._team._team.goal.domain.enums.GoalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalAggregateResDto {
    private UUID orgGoalId;
    private int childCount;
    private int confirmedCount;
    private BigDecimal weightedAvgScore;
    private BigDecimal simpleAvgScore;
    private List<UUID> childGoalIds;
    private Map<GoalStatus, Long> childCountByStatus;

    public static Map<GoalStatus, Long> emptyStatusMapWith(Map<GoalStatus, Long> input) {
        Map<GoalStatus, Long> out = new EnumMap<>(GoalStatus.class);
        for (GoalStatus status : GoalStatus.values()) {
            out.put(status, input.getOrDefault(status, 0L));
        }
        return out;
    }
}
