package com._team._team.attendance.dto.reqDto;

import com._team._team.attendance.domain.enums.OvertimeRequestType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 연장근로 신청 DTO
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class OvertimeRequestCreateReqDto {

    @NotNull
    private LocalDate targetDate;

    @NotNull
    private OvertimeRequestType requestType;

    // 사전 신청용
    private LocalTime plannedStartTime;
    private LocalTime plannedEndTime;

    @Positive
    private Integer requestedMinutes;

    // 사후 신청용
    private LocalTime actualStartTime;
    private LocalTime actualEndTime;

    @Positive
    private Integer actualMinutes;

    @Size(max = 500)
    private String reason;
}