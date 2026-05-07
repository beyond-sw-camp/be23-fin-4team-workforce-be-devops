package com._team._team.goal.domain.enums;

/**
 * 승인 번들의 성격을 구분한다.
 *
 * - ACTIVATION : DRAFT 상태의 목표를 ACTIVE 로 활성화하기 위한 승인
 * - COMPLETION : ACTIVE 상태의 목표를 COMPLETED 로 종료하기 위한 승인
 *
 * {@link GoalApprovalBundle} 에 부착되며 어떤 단계에서 승인 절차가 필요한지를 결정한다.
 */
public enum BundleApprovalKind {
    ACTIVATION,
    COMPLETION
}
