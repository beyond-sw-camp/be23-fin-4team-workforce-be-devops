package com._team._team.esg.dtos.reqdto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EsgConfigUpdateReqDto {

    private String esgEnabledYn;
    private int monthlyPointLimit;
}