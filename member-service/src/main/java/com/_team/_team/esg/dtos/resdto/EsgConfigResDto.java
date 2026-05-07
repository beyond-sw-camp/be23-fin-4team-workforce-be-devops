package com._team._team.esg.dtos.resdto;

import com._team._team.esg.domain.EsgCompanyConfig;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EsgConfigResDto {

    private String esgEnabledYn;
    private int monthlyPointLimit;

    public static EsgConfigResDto fromEntity(EsgCompanyConfig config) {
        return EsgConfigResDto.builder()
                .esgEnabledYn(config.getEsgEnabledYn())
                .monthlyPointLimit(config.getMonthlyPointLimit())
                .build();
    }
}