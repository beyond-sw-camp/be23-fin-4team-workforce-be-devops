package com._team._team.salary.dto.reqdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 급여대장 재계산 요청 DTO
 * - 회사 관리자가 자기 회사 직원 급여를 다시 계산할 때 사용
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PayrollRecalculateReqDto {

    /** 정산 연월일 - 없으면 정책 기준 다음 정산일 자동 */
    private LocalDate settlementDate;
}
