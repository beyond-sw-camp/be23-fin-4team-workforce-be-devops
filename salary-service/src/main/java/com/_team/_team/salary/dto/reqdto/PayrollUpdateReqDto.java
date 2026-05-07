package com._team._team.salary.dto.reqdto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PayrollUpdateReqDto {

    /** 정산 연월일 */
    @NotNull(message = "정산 연월일은 필수입니다.")
    private LocalDate payrollYearMonthDay;

    /** 총 지급액 */
    @NotNull(message = "총 지급액은 필수입니다.")
    private Long totalPayment;

    /** 총 공제액 */
    @NotNull(message = "총 공제액은 필수입니다.")
    private Long totalDeduction;

    /** 실수령액 */
    @NotNull(message = "실수령액은 필수입니다.")
    private Long netPay;
}
