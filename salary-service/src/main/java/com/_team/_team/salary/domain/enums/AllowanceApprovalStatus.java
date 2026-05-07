package com._team._team.salary.domain.enums;

/**
 * 수당 결재 상태
 * PENDING    결재 대기
 * APPROVED   승인, 실제 급여 반영
 * REJECTED   반려
 * CANCELLED  본인 철회
 * AUTO       입사 시 시스템 자동 세팅, 결재 생략
 */
public enum AllowanceApprovalStatus {
    PENDING, APPROVED, REJECTED, CANCELLED, AUTO
}
