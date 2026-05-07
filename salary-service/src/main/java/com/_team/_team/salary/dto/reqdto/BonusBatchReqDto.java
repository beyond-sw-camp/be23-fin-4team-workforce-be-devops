package com._team._team.salary.dto.reqdto;

import com._team._team.salary.domain.enums.BonusKind;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BonusBatchReqDto {
    @NotNull
    private BonusKind bonusKind;

    @NotNull
    private LocalDate payDate;

    /** REGULAR: 1회 지급 비율 (% / 정책 연누계 / 지급횟수 prefill 가능)
     *  PERFORMANCE: 입력 비율 (% / 정책 max 한도 검증)
     *  HOLIDAY (RATE): 정책 holidayBonusValue 사용 - 무시
     *  HOLIDAY (AMOUNT): 정책 holidayBonusValue 정액 사용 - 무시 */
    private BigDecimal ratePercent;

    /** 메모 / 사유 */
    private String memo;
}
