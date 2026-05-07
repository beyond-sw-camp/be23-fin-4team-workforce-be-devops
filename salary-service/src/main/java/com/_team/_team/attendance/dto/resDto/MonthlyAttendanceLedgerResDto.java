package com._team._team.attendance.dto.resDto;

import com._team._team.attendance.domain.MonthlyAttendanceLedger;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 월 근태 장부 응답 DTO
 *
 * 관리자 월별 근태 리포트 화면
 * 급여 서비스 내부 조회 (모듈 내부 호출)
 * 직원 본인 월 근태 요약 조회
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class MonthlyAttendanceLedgerResDto {

    private UUID ledgerId;
    private UUID memberId;
    private String yearMonth;

    private Integer totalWorkedMinutes;
    private Integer regularMinutes;
    private Integer overtimeMinutes;
    private Integer nightMinutes;
    private Integer holidayMinutes;

    private Integer leaveMinutes;
    private Integer lateMinutes;
    private Integer earlyLeaveMinutes;
    private Integer absentDays;
    private String leaveBreakdownJson;

    private LocalDateTime closedAt;
    private UUID closedBy;
    private Boolean isLocked;

    public static MonthlyAttendanceLedgerResDto fromEntity(MonthlyAttendanceLedger l) {
        return MonthlyAttendanceLedgerResDto.builder()
                .ledgerId(l.getLedgerId())
                .memberId(l.getMemberId())
                .yearMonth(l.getLedgerYearMonth())
                .totalWorkedMinutes(l.getTotalWorkedMinutes())
                .regularMinutes(l.getRegularMinutes())
                .overtimeMinutes(l.getOvertimeMinutes())
                .nightMinutes(l.getNightMinutes())
                .holidayMinutes(l.getHolidayMinutes())
                .leaveMinutes(l.getLeaveMinutes())
                .lateMinutes(l.getLateMinutes())
                .earlyLeaveMinutes(l.getEarlyLeaveMinutes())
                .absentDays(l.getAbsentDays())
                .leaveBreakdownJson(l.getLeaveBreakdownJson())
                .closedAt(l.getClosedAt())
                .closedBy(l.getClosedBy())
                .isLocked(l.getIsLocked())
                .build();
    }
}
