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
    private String jobKey;          // "name::group" 형식
    private String triggerKey;      // "name::group" 형식
    private String cronExpression;  // 현재 cron
    private Date nextFireTime;
    private Date previousFireTime;
    private boolean paused;         // 일시중지 상태 여부
}
