package com._team._team.attendance.dto.reqDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 출퇴근 정정 신청
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class AttendanceCorrectionReqDto {

    @NotNull(message = "근태 일자는 필수입니다.")
    private LocalDate attendanceDate;

    /** 정정 출근 시각 (선택) */
    private LocalDateTime requestedClockIn;

    /** 정정 퇴근 시각 (선택) */
    private LocalDateTime requestedClockOut;

    @NotBlank(message = "정정 사유는 필수입니다.")
    private String reason;
}
