package com._team._team.attendance.domain.enums;

/**
 * 휴가 신청 주체
 * SELF               : 직원 본인이 신청 (기본)
 * ADMIN_DESIGNATION  : 회사가 노무수령 거부 절차로 강제 지정
 */
public enum LeaveInitiator {
    SELF,                   // 직원 본인이 신청 (기본)
    ADMIN_DESIGNATION       // 회사가 노무수령 거부 절차로 강제 지정
}