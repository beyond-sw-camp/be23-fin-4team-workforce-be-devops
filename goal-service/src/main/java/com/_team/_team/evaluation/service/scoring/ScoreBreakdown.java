package com._team._team.evaluation.service.scoring;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * [L-1] 응답 전체 채점 결과 VO — 섹션 리스트 + 총점.
 *
 * 프론트 결과 화면에서 "이 응답 점수의 몇 %가 KPI 에서, 몇 %가 매뉴얼 섹션에서 왔는지"
 * 시각화할 때 활용한다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScoreBreakdown {

    /** 가중 평균 총점 (0~100). 채점 가능한 섹션이 하나도 없으면 null. */
    private BigDecimal totalScore;

    @Builder.Default
    private List<SectionScore> sections = new ArrayList<>();
}
