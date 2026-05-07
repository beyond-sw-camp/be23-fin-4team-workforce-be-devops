package com._team._team.attendance.dto.vo;

import com._team._team.attendance.domain.enums.ViolationType;

/**
 * 법정 한도 위반 내역 (값 객체, DB 저장 x)
 *   type            위반 유형
 *   message         사용자 노출용 메시지
 *   actualMinutes   실제 기록된 분
 *   limitMinutes    한도 분
 */
public record LaborLawViolation(
        ViolationType type,
        String message,
        int actualMinutes,
        int limitMinutes
) {

    // 초과량 (분)
    public int exceededBy() {
        return Math.max(0, actualMinutes - limitMinutes);
    }
}