package com._team._team.attendance.dto.resDto;

import com._team._team.attendance.domain.DailyAttendance;
import com._team._team.attendance.domain.WorkTripDetail;
import com._team._team.attendance.domain.enums.AttendanceStatus;
import com._team._team.attendance.domain.enums.ClosureStatus;
import com._team._team.attendance.domain.enums.CorrectionState;
import com._team._team.attendance.domain.enums.WorkTripType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;


/**
 * 일별 근태 요약 응답 DTO
 * - 월간 근태 조회 시 이 DTO 리스트로 응답 (attendance_log JOIN 없음)
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class DailyAttendanceResDto {

    private UUID dailyAttendanceId;
    private UUID memberId;
    private LocalDate attendanceDate;
    private AttendanceStatus status;
    private ClosureStatus closureStatus;
    private UUID workScheduleId;
    private LocalDateTime firstClockIn;
    private LocalDateTime lastClockOut;
    private Integer workedMinutes;
    private Integer overtimeMinutes;
    /** 정정 진행 상태 — 직원 화면 액션 컬럼 분기에 사용 */
    private CorrectionState correctionState;

    /** 출장/외근 */
    private WorkTripType workTripType;

    /** 조퇴계 결재 승인 여부 - Y면 상태 표시/카운트에 '조퇴' 반영 */
    private String earlyLeaveExcusedYn;


    public static DailyAttendanceResDto fromEntity(DailyAttendance daily) {
        WorkTripType tripType = null;
        if (daily.getTrips() != null) {
            tripType = daily.getTrips().stream()
                    .filter(t -> "N".equals(t.getDelYn()))
                    .findFirst()
                    .map(WorkTripDetail::getWorkTripType)
                    .orElse(null);
        }
        return DailyAttendanceResDto.builder()
                .dailyAttendanceId(daily.getDailyAttendanceId())
                .memberId(daily.getMemberId())
                .attendanceDate(daily.getAttendanceDate())
                .status(daily.getStatus())
                .closureStatus(daily.getClosureStatus())
                .workScheduleId(daily.getWorkScheduleId())
                .firstClockIn(daily.getFirstClockIn())
                .lastClockOut(daily.getLastClockOut())
                .workedMinutes(daily.getWorkedMinutes())
                .overtimeMinutes(daily.getOvertimeMinutes())
                .correctionState(CorrectionState.NORMAL)   // 기본값. Service 에서 보정
                .workTripType(tripType)
                .earlyLeaveExcusedYn(daily.getEarlyLeaveExcusedYn())
                .build();
    }

    public static DailyAttendanceResDto fromEntity(DailyAttendance daily, CorrectionState state) {
        DailyAttendanceResDto dto = fromEntity(daily);
        dto.setCorrectionState(state);
        return dto;
    }
}
