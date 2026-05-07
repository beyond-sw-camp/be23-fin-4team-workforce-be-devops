package com._team._team.evaluation.service.scoring;

import com._team._team.evaluation.domain.enums.SectionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * [L-1] 섹션별 채점 결과 VO.
 *
 * 저장되지 않고, 응답 조회 시 design + answers + goalSnapshot 로부터 재계산된다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SectionScore {

    private String sectionId;
    private String title;
    private SectionType type;

    /** 섹션 가중치 (원본). 합산 100 기준 가정. */
    private BigDecimal weight;

    /** 0~100 스케일 점수. 데이터가 부족해 채점 불가능한 경우 null. */
    private BigDecimal score;

    /** 채점이 스킵되었는지 여부. 스킵된 섹션은 총점 가중 평균에서 제외된다. */
    private boolean skipped;

    /** 스킵된 이유 — 디버깅/UI 표시용. */
    private String reason;

    /** 섹션에 사용된 신호 개수(답변 수 / 스냅샷 수). UI 에서 근거 표시용. */
    private Integer sampleSize;

    /**
     * [KPI_SCORE 섹션 전용] 개별 목표가 이 섹션 점수에 기여한 상세.
     * 결과 화면에서 "어떤 목표가 얼마만큼 점수에 반영됐는가" 를 보여주기 위한 드릴다운 정보.
     * 스키마 변경 없이 조회 시점 재계산으로만 채워진다.
     * MANUAL / PEER_FEEDBACK 섹션에서는 null.
     */
    private List<KpiContribution> kpiContributions;

    /**
     * KPI 섹션에서 한 목표(goalSnapshot) 가 얼마나 기여했는지.
     *   achievement 가 사용된 달성률(rolledAchievementPctAtSnapshot ?? achievementPctAtSnapshot).
     *   weight 는 Goal.weightPct (>0 이면 사용, 0/null 이면 1.0 균등 분배 간주).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class KpiContribution {
        private UUID goalId;
        private String title;
        /** 이 목표의 달성률 (0~100 또는 capPct 까지). */
        private BigDecimal achievement;
        /** 가중치 (Goal.weightPct, 없으면 null). */
        private BigDecimal weight;
        /** 섹션 점수에 실제로 기여한 비율(0~100). 시각화용. */
        private BigDecimal contributionPct;
    }
}
