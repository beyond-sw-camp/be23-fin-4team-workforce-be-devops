package com._team._team.attendance.domain.enums;

/**
 * 연장근로 신청 결재 상태
 *
 * PENDING    제출, 결재 대기
 * APPROVED   승인
 * REJECTED   반려
 * CANCELLED  본인이 결재 전 취소
 * EXPIRED    사후 신청인데 72시간 초과로 자동 만료
 */
public enum OvertimeApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    EXPIRED
}
