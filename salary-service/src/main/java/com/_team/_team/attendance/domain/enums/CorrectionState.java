package com._team._team.attendance.domain.enums;

/**
 * 일별 근태의 정정(correction) 진행 상태
 *  NORMAL     정상 — 출퇴근 모두 찍혔거나 휴가·미래 일자 등 액션 불필요
 *  ABNORMAL   이상 — 출근 또는 퇴근 한쪽 이상 누락, 정정 신청 대상
 *  PENDING    검토중 — 직원이 정정 신청 후 관리자 결정 대기 (closureStatus = UNDER_REVIEW)
 *  COMPLETED  정정 완료 — ADMIN_MANUAL AttendanceLog 가 isCorrectedYn=Y + correctedBy 박힘
 */
public enum CorrectionState {
    NORMAL,
    ABNORMAL,
    PENDING,
    COMPLETED
}
