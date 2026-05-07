package com._team._team.evaluation.domain.converter;

import com._team._team.evaluation.domain.enums.EvalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 평가 그룹의 대상자 ↔ 평가자 매핑 값 객체.
 * [D-3] 기존 evaluator_maps_json raw JSON 을 타입 안전하게 대체.
 *
 *   targetMemberId : 평가 대상자
 *   evaluatorId    : 평가 수행자 (SELF 의 경우 target 과 동일)
 *   evaluationType : SELF/DOWNWARD/UPWARD/PEER
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluatorMapping {
    private UUID targetMemberId;
    private UUID evaluatorId;
    private EvalType evaluationType;
}
