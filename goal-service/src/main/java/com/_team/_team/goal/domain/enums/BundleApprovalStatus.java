package com._team._team.goal.domain.enums;

/**
 * Bundle 상태.
 *  PENDING   : 권한자 결정 대기
 *  APPROVED  : 승인 완료 (모든 goal ACTIVE 전이됨)
 *  REJECTED  : 반려 (모든 goal DRAFT 복귀)
 *  WITHDRAWN : 요청자 회수 (모든 goal DRAFT 복귀)
 */
public enum BundleApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    WITHDRAWN
}
