package com._team._team.salary.dto.reqdto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.YearMonth;
import java.util.UUID;

/**
 * 소급분 자동 재계산 요청
 *  통상임금 인상이 단협으로 결정되어 과거 월 가산수당을 새 통상임금 기준으로 재계산
 *  preview 와 apply 양쪽에서 사용 동일한 페이로드
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class RetroactivePayrollReqDto {

    @NotNull(message = "대상 직원 ID 는 필수입니다.")
    private UUID memberId;

    // 소급 시작월 YYYY-MM 형식
    @NotNull(message = "소급 시작월은 필수입니다.")
    private YearMonth fromMonth;

    // 소급 종료월 YYYY-MM 형식 fromMonth 이상이어야 함
    @NotNull(message = "소급 종료월은 필수입니다.")
    private YearMonth toMonth;

    // 새 통상임금 (인상 후) 가산수당 base
    @NotNull(message = "새 통상임금은 필수입니다.")
    @Min(value = 0, message = "통상임금은 0 이상이어야 합니다.")
    private Long newOrdinaryWage;

    // 발행 사유 / 메모 (선택)
    private String memo;
}
