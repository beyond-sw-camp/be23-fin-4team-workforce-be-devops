package com._team._team.attendance.domain.enums;

/**
 * 연장근로 신청 타입
 *
 * PRE 사전 신청 (근무 전 미리 신청)
 * - 주 52시간 사전 차단 대상
 *
 * POST사후 신청 (실제 근무 후 신청)
 */
public enum OvertimeRequestType {
    PRE,
    POST
}
