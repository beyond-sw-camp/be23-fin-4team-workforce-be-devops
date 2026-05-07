package com._team._team.attendance.dto.resDto;

import lombok.*;

import java.time.LocalDate;

/**
 * 정정 후보 일자 (직원 본인용)
 *  - 휴가·휴직 복귀자 안전망
 *  - reasonCode:
 *      NO_RECORD          DailyAttendance 자체가 없음 (출퇴근 모두 안 찍음)
 *      CLOCK_IN_MISSING   출근만 누락
 *      CLOCK_OUT_MISSING  퇴근만 누락
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class MissingAttendanceSuspectResDto {

    private LocalDate date;
    private String reasonCode;

    /** 후보 사유 한글 */
    public String getReasonLabel() {
        return switch (reasonCode == null ? "" : reasonCode) {
            case "NO_RECORD" -> "출·퇴근 둘 다 미체크";
            case "CLOCK_IN_MISSING" -> "출근 미체크";
            case "CLOCK_OUT_MISSING" -> "퇴근 미체크";
            default -> "정정 검토 필요";
        };
    }
}
