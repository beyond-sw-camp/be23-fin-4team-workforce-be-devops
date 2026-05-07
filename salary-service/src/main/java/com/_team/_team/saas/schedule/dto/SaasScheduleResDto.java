package com._team._team.saas.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class SaasScheduleResDto {
    private String jobKey;
    private String triggerKey;
    private String cronExpression;
    private Date nextFireTime;
    private Date previousFireTime;
    private boolean paused;
}
