package com._team._team.goal.domain.enums;

/**
 * KPI 템플릿 단위 승인 정책. 기존 {@code requireApproval} 불리언을 확장한 개념이다.
 *
 * - NONE             : 승인 절차 없음
 * - ACTIVATION_ONLY  : 목표 활성화(DRAFT → ACTIVE) 시에만 승인 필요
 * - COMPLETION_ONLY  : 목표 종료(ACTIVE → COMPLETED) 시에만 승인 필요
 * - BOTH             : 활성화와 종료 모두 승인 필요
 *
 * {@link BundleApprovalKind} 와 조합되어 번들 생성 요청을 차단하거나 허용한다.
 */
public enum GoalApprovalPolicy {
    NONE,
    ACTIVATION_ONLY,
    COMPLETION_ONLY,
    BOTH;

    public boolean requiresActivation() {
        return this == ACTIVATION_ONLY || this == BOTH;
    }

    public boolean requiresCompletion() {
        return this == COMPLETION_ONLY || this == BOTH;
    }

    public boolean requires(BundleApprovalKind kind) {
        if (kind == null) return this != NONE;
        return switch (kind) {
            case ACTIVATION -> requiresActivation();
            case COMPLETION -> requiresCompletion();
        };
    }

    /**
     * 기존 requireApproval 불리언 호환 변환기.
     * true  → BOTH (기존 플래그는 단계 구분이 없었으므로 안전한 기본값)
     * false → NONE
     */
    public static GoalApprovalPolicy fromLegacyFlag(boolean requireApproval) {
        return requireApproval ? BOTH : NONE;
    }
}
