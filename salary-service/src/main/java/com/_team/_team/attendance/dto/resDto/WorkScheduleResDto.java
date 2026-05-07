package com._team._team.attendance.dto.resDto;

import com._team._team.attendance.domain.WorkSchedule;
import com._team._team.attendance.domain.enums.WorkType;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class WorkScheduleResDto {

    private UUID workScheduleId;
    private UUID companyId;
    private UUID memberId;
    private String scheduleName;
    private WorkType workType;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer workMinutes;
    /** FIXED 회사 정책 점심 시작 시각, FLEXIBLE 은 null */
    private LocalTime breakStart;
    /** FIXED 회사 정책 점심 종료 시각, FLEXIBLE 은 null */
    private LocalTime breakEnd;
    /** breakStart/breakEnd 로부터 계산한 점심 분 */
    private Integer breakMinutes;
    private Integer selectionDeadlineDay;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    public static WorkScheduleResDto fromEntity(WorkSchedule workSchedule) {
        return WorkScheduleResDto.builder()
                .workScheduleId(workSchedule.getWorkScheduleId())
                .companyId(workSchedule.getCompanyId())
                .memberId(workSchedule.getMemberId())
                .scheduleName(workSchedule.getScheduleName())
                .workType(workSchedule.getWorkType())
                .startTime(workSchedule.getStartTime())
                .endTime(workSchedule.getEndTime())
                .workMinutes(workSchedule.getWorkMinutes())
                .breakStart(workSchedule.getBreakStart())
                .breakEnd(workSchedule.getBreakEnd())
                .breakMinutes(workSchedule.computeBreakMinutes())
                .selectionDeadlineDay(workSchedule.getSelectionDeadlineDay())
                .effectiveFrom(workSchedule.getEffectiveFrom())
                .effectiveTo(workSchedule.getEffectiveTo())
                .build();
    }
}
