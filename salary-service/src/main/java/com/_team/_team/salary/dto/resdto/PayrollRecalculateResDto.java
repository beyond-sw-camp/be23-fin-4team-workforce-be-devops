package com._team._team.salary.dto.resdto;

import com._team._team.batch.payroll.worker.PayrollCalculateWorker;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 급여대장 재계산 결과 응답 DTO
 * - 처리 건수 통계만 노출
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PayrollRecalculateResDto {

    /** 신규 생성된 급여대장 수 */
    private int created;
    /** 이미 존재해 스킵된 수 */
    private int duplicateSkip;
    /** 활성 Salary 없어서 스킵된 수 */
    private int noSalary;
    /** 그 외 비즈니스 예외 수 */
    private int badRequest;
    /** 시스템 예외 수 */
    private int fail;

    public static PayrollRecalculateResDto from(PayrollCalculateWorker.Counter c) {
        return PayrollRecalculateResDto.builder()
                .created(c.ok)
                .duplicateSkip(c.dup)
                .noSalary(c.noSalary)
                .badRequest(c.badReq)
                .fail(c.fail)
                .build();
    }
}
