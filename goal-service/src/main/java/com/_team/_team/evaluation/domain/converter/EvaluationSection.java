package com._team._team.evaluation.domain.converter;

import com._team._team.evaluation.domain.enums.SectionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * [D-4 + L-1 Step 1] 평가 설계 섹션 VO.
 *
 * 기존 프론트 DesignSection 과 호환:
 *   - title, weight, questions (문항 리스트)
 *
 * 추가 필드 (L-1):
 *   - sectionId : 섹션 식별자 (프론트가 넘기지 않으면 서버에서 생성)
 *   - type      : MANUAL (기본) / KPI_SCORE / PEER_FEEDBACK
 *   - kpiFilter : KPI_SCORE 섹션의 집계 범위 (옵션)
 *
 * 스코어링 로직은 Phase C 에서 연결. 지금은 데이터 모델만 확장.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EvaluationSection {
    /** 섹션 식별자 — 없으면 프론트가 생성하거나 서버가 나중에 채움 */
    private String sectionId;

    private String title;

    /** 섹션 가중치 (합산 100% 기준) */
    private BigDecimal weight;

    /** 섹션 유형 — 기본값은 MANUAL. null 이면 MANUAL 로 해석. */
    private SectionType type;

    /**
     * KPI_SCORE 섹션 전용 — 어떤 KPI 를 집계할지 필터.
     * 최종 모델에서는 템플릿 분기 없이 goal snapshot 집합 기준으로만 처리한다.
     */
    private String kpiFilter;

    /** 문항 목록 — 기존 DesignQuestion 구조 그대로 유지. */
    @Builder.Default
    private List<DesignQuestion> questions = new ArrayList<>();

    /**
     * type 이 null 일 때 기본값 MANUAL 을 반환. 호출부에서 null 체크 간편화.
     */
    public SectionType resolveType() {
        return this.type != null ? this.type : SectionType.MANUAL;
    }
}
