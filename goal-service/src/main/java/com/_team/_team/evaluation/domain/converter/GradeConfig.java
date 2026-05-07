package com._team._team.evaluation.domain.converter;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * [D-4] 등급(grade) 산정 설정 VO.
 *
 * 프론트 GradeConfig 구조와 1:1 매칭:
 *   type                : "ABSOLUTE" | "RELATIVE"
 *   grades              : 등급 구간 정의
 *   targetDistribution  : RELATIVE 사용 시 목표 분포 (옵션)
 *
 * 검증/계산 로직은 애플리케이션 레벨. 여기서는 JSON 매핑만 담당.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GradeConfig {

    /** "ABSOLUTE" (절대평가) | "RELATIVE" (상대평가) */
    private String type;

    @Builder.Default
    private List<GradeBand> grades = new ArrayList<>();

    /** RELATIVE 용: 등급 라벨 → 목표 비율 (e.g. { "S": 0.1, "A": 0.3, ... }) */
    private Map<String, BigDecimal> targetDistribution;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GradeBand {
        private String label;
        private BigDecimal minScore;
        private BigDecimal maxScore;
        private String color;
    }
}
