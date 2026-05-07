package com._team._team.attendance.domain.enums;

/**
 * 연장근로 승인 모드
 * PRE_ONLY   사전 신청만 허용, 당일,사후 신청 불가
 * POST_ONLY  사후 승인만 허용, 실제 근무 후 신청
 * HYBRID     사전·사후 둘 다 허용 (기본값)
 */
public enum ApprovalMode {
    PRE_ONLY,
    POST_ONLY,
    HYBRID
}
