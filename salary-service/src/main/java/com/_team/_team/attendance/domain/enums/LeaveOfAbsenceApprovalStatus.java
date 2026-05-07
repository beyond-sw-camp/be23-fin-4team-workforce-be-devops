package com._team._team.attendance.domain.enums;

/**
 * 휴직 결재/진행 상태
 */
public enum LeaveOfAbsenceApprovalStatus {
    REQUESTED,    // 신청, 결재 대기
    ACTIVE,       // 승인 후 휴직 중
    ENDED,        // 복직 완료 (자연 종료 or 조기 종료)
    REJECTED,     // 결재 반려
    CANCELLED     // 본인 철회
}