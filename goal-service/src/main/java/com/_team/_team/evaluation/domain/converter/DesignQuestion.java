package com._team._team.evaluation.domain.converter;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * [D-4] 평가 문항 VO.
 * 기존 프론트 DesignQuestion 구조와 호환.
 *
 *   type       : "text" | "scale" | "grade" | "gap" (프론트 QuestionType)
 *   required   : 필수 여부
 *   weight     : 섹션 내 가중치
 *   options    : scale/grade 용 옵션 (자유 스키마 — 검증은 애플리케이션 레벨)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DesignQuestion {
    private String id;
    private String type;
    private String title;
    private String description;
    private Boolean required;
    private BigDecimal weight;
    private Map<String, Object> options;
}
