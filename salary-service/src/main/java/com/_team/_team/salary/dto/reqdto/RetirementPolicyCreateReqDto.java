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
public class RetirementPolicyCreateReqDto {

    @NotNull(message = "퇴직급여 제도는 필수입니다.")
    private RetirementType retirementType;

    @NotNull(message = "적용 시작일은 필수입니다.")
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private String memo;

    // DC 형 월 부담금 비율(%) - DC 형 정책일 때만 사용, 미입력 시 8.33 적용
    private BigDecimal dcContributionRate;

    // 운용 금융기관명
    private String providerName;

    // 운용 계약/계좌번호
    private String contractNumber;

    // 중간정산 허용 여부 - LEGAL 형에서만 의미, Y/N
    private String allowEarlySettlementYn;
}