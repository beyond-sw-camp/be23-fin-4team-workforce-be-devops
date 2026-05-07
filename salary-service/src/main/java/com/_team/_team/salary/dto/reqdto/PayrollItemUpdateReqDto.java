package com._team._team.salary.dto.reqdto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 급여대장 항목 수정 요청 DTO
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PayrollItemUpdateReqDto {

    /** 금액 */
    @NotNull(message = "금액은 필수입니다.")
    private Long amount;

    private Integer displayOrder;
}
