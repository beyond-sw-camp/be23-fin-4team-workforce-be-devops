package com._team._team.attendance.domain.enums;
/**
 * 경비 처리 상태
 * - PENDING → APPROVED/REJECTED (approval-service 연동)
 */
public enum ExpenseStatus {
    PENDING, // 승인요청
    APPROVED, // 승인
    REJECTED  // 반려
}
