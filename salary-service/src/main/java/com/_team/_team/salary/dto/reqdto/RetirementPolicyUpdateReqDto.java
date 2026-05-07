package com._team._team.salary.dto.reqdto;

import com._team._team.salary.domain.enums.RetirementType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class RetirementPolicyUpdateReqDto {

    @NotNull(message = "퇴직급여 제도는 필수입니다.")
    private RetirementType retirementType;

    private LocalDate effectiveTo;

    private String memo;

    // DC 형 월 부담금 비율(%)
    private BigDecimal dcContributionRate;

    private String providerName;

    private String contractNumber;

    // LEGAL 형 중간정산 허용 여부 Y/N
    private String allowEarlySettlementYn;
}