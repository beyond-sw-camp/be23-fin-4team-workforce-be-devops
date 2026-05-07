package com._team._team.salary.dto.resdto;

import com._team._team.salary.domain.enums.BonusKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BonusBatchPreviewResDto {

    private BonusKind bonusKind;
    private LocalDate payDate;
    /** 정책 한도 비율 (%) - PERFORMANCE / REGULAR 만 의미 있음 */
    private Double policyMaxRate;
    /** 발행 적용 비율 (%) */
    private Double appliedRate;

    private int totalEligible;       // 시뮬 대상 수 (필터 통과)
    private int totalSkipped;        // 자격 미충족으로 스킵된 수
    private long totalGrossAmount;   // 총 지급 합계 (세전)

    private List<TargetEntry> targets;

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TargetEntry {
        private UUID memberId;
        private String name;
        private String sabun;
        private String organizationName;
        private long baseSalary;
        private long bonusAmount;
        /** 한도 초과 여부 (PERFORMANCE 시) */
        private boolean exceedsLimit;
        /** 스킵 사유 - null 이면 정상 대상 */
        private String skipReason;
    }
}
