package com._team._team.evaluation.domain.enums;

/**
 * 다중 평가자 역할.
 *  LEAD      : 직속 조직장 (자동 매핑) 또는 시즌 admin override.
 *              finalGrade 입력 + confirm 권한 보유. 시즌당 응답 1건에 1명 강제.
 *  ASSISTANT : 보조 평가자. suggestedGrade 만 입력. 미제출 시 자동 SKIP.
 */
public enum CalibrationRole {
    LEAD,
    ASSISTANT
}
