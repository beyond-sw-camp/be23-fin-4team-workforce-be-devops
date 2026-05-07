package com._team._team.salary.dto.reqdto;

import com._team._team.salary.domain.SalaryPolicy;
import com._team._team.salary.domain.enums.PayCycleType;
import com._team._team.salary.domain.enums.PayDayShiftRule;
import com._team._team.salary.domain.enums.ProrationMethod;
import com._team._team.salary.domain.enums.WageSystemType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class SalaryPolicyCreateReqDto {

    @NotNull(message = "정책명은 필수 입니다.")
    private String policyName;

    @NotNull(message = "지급일은 필수 입니다.")
    @Min(value=1, message = "지급일은 1이상이어야합니다.")
    @Max(value=31, message = "지급일은 31이하여야합니다.")
    private Integer payDay;

    @Builder.Default
    private PayDayShiftRule payDayShiftRule = PayDayShiftRule.BEFORE;

    /**
     * 급여 지급 주기 (당월분/전월분), 기본 CURRENT_MONTH
     */
    @Builder.Default
    private PayCycleType payCycleType = PayCycleType.CURRENT_MONTH;

    @NotNull(message = "호봉제 또는 연봉협상제 선택은 필수 입니다.")
    @Builder.Default
    private String usePayGradeYn = "N";

    @NotNull(message = "임금제 유형은 필수입니다.")
    private WageSystemType wageSystemType;

    /**
     * 포괄임금제일 때 포함된 고정 오버타임시간(분)
     * - COMPREHENSIVE이면 1 이상 필수
     * - NON_COMPREHENSIVE이면 0
     */
    @Min(value = 0, message = "고정 오버타임시간(분)은 0 이상이어야 합니다.")
    private Integer fixedOvertimeMinutes;

    // 월 소정근로시간 시급 환산 기준
    // 한국 표준 209h 주 35h 회사 183h 등 회사별 다름
    @Min(value = 1, message = "월 소정근로시간은 1 이상이어야 합니다.")
    @Max(value = 300, message = "월 소정근로시간은 300 이하여야 합니다.")
    @Builder.Default
    private Integer monthlyOrdinaryHours = 209;

    // 일할계산 방식 입사 / 퇴사 / 기간변경 월 적용
    @Builder.Default
    private ProrationMethod prorationMethod = ProrationMethod.DAYS_IN_MONTH;

    @NotNull(message = "적용 시작일은 필수입니다.")
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    public SalaryPolicy toEntity(UUID companyId) {
        return SalaryPolicy.builder()
                .companyId(companyId)
                .policyName(policyName)
                .payDay(payDay)
                .payDayShiftRule(payDayShiftRule)
                .usePayGradeYn(usePayGradeYn)
                .effectiveFrom(effectiveFrom)
                .effectiveTo(effectiveTo)
                .wageSystemType(wageSystemType)
                .fixedOvertimeMinutes(
                        wageSystemType == WageSystemType.NON_COMPREHENSIVE
                                ? 0
                                : (fixedOvertimeMinutes == null ? 0 : fixedOvertimeMinutes)
                )
                .monthlyOrdinaryHours(
                        monthlyOrdinaryHours == null || monthlyOrdinaryHours <= 0
                                ? 209
                                : monthlyOrdinaryHours
                )
                .prorationMethod(
                        prorationMethod == null
                                ? ProrationMethod.DAYS_IN_MONTH
                                : prorationMethod
                )
                .payCycleType(payCycleType == null ? PayCycleType.CURRENT_MONTH : payCycleType)
                .build();
    }
}
