package com._team._team.evaluation.dto.resdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeasonOperationalAlertsResDto {

    private UUID seasonId;
    private int totalAlerts;
    private List<AlertItem> alerts;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AlertItem {
        private String alertType;
        private String severity;
        private UUID responseId;
        private UUID targetMemberId;
        private UUID evaluatorId;
        private String targetMemberStatus;
        private String evaluatorStatus;
        private String message;
        private String recommendedAction;
    }
}
