package com._team._team.evaluation.dto.resdto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 평가 응답 화면에서 노출할 목표별 요약 카드.
 *
 * - snapshot    : 평가 시즌 시작 시점에 캡처된 값 (goalSnapshotJson 에서 복원)
 * - current     : 현재 DB 상의 최신 값 (실시간 조회)
 * - 두 값이 다르면 UI 에서 "스냅샷 이후 변경됨" 배지를 표시한다.
 *
 * 새 엔티티 없이 EvaluationResponse + Goal 조회만으로 구성한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GoalSummaryCardResDto {

    private UUID goalId;

    /** 시즌 시작 시점에 캡처된 스냅샷. Null 이면 평가 생성 이후 추가된 목표. */
    private GoalSnapshotDto snapshot;

    /** 현재 최신 상태. Null 이면 목표가 삭제/비공개 전환된 경우. */
    private CurrentGoalView current;

    /** 스냅샷 대비 현재 값이 변경되었는지 여부. */
    private boolean changedSinceSnapshot;

    /** 변경 유형 요약 (제목/목표치/기간/상태 중 무엇이 바뀌었는지). */
    private List<String> changeSummary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CurrentGoalView {
        private String title;
        private String status;
        private BigDecimal targetValue;
        private BigDecimal actualValue;
        private BigDecimal achievementPct;
        private BigDecimal rolledAchievementPct;
        private BigDecimal weightPct;
        private String unitLabel;
    }
}
