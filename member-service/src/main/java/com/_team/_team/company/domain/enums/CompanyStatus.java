package com._team._team.company.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CompanyStatus {
    ACTIVE,  //정상
    SUSPENDED, //정지(요금 미납 등)
    DELETED // 해지
}
