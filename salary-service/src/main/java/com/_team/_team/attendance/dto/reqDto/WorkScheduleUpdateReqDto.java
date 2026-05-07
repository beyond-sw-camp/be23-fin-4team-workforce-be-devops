package com._team._team.attendance.dto.reqDto;

import com._team._team.attendance.domain.enums.WorkType;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class WorkScheduleUpdateReqDto {

    private String scheduleName;
    private WorkType workType;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer workMinutes;
    /** FIXED 정책의 점심·휴게 시작 시각 */
    private LocalTime breakStart;
    /** FIXED 정책의 점심·휴게 종료 시각 */
    private LocalTime breakEnd;
    private Integer selectionDeadlineDay;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}