package com._team._team.saas.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class SaasScheduleUpdateReqDto {
    @NotBlank(message = "cron은 필수입니다.")
    private String cron;
}
