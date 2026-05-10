package com._team._team.salary.dto.reqdto;

import com._team._team.salary.domain.enums.BonusKind;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class BonusBatchReqDto {
    @NotNull
    private BonusKind bonusKind;

    @NotNull
    private LocalDate payDate;
    private BigDecimal ratePercent;

    /** 메모 / 사유 */
    private String memo;

    /** 직원별 차등 발행
     *  성과급은 인사평가 결과 등급별 차등 지급에 사용*/
    private List<MemberItem> items;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class MemberItem {
        private UUID memberId;
        // 행별 지급 비율 - null/0이면 미지급
        private BigDecimal ratePercent;
    }
}
