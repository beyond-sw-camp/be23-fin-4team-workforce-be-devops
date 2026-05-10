package com._team._team.attendance.dto.reqDto;

import com._team._team.attendance.domain.enums.ApprovalMode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class OvertimePolicyUpdateReqDto {

    @Positive
    private Integer overtimeFloorMinutes;

    private ApprovalMode approvalMode;

    @Positive
    private Integer postApprovalDeadlineHours;

    @Positive
    private Integer weeklyOvertimeLimitMinutes;

    @Positive
    private Integer weeklyTotalLimitMinutes;

    @Positive
    private Integer dailyOvertimeLimitMinutes;

    @Positive
    private Integer monthlyOvertimeLimitMinutes;

    private Boolean holidayWorkRequiresApproval;

    /** 적용 시작일 변경 (null 이면 기존 값 유지) */
    private LocalDate effectiveFrom;

    /** 적용 종료일 변경, 어제 날짜로 설정하면 정책 종료 처리*/
    private LocalDate effectiveTo;

    /** overtimeFloorMinutes 가 15분 또는 30분 만 허용 */
    @AssertTrue(message = "연장근로 인정 단위는 15분 또는 30분만 가능합니다.")
    public boolean isValidOvertimeFloorMinutes() {
        return overtimeFloorMinutes != null
                && (overtimeFloorMinutes == 15 || overtimeFloorMinutes == 30);
    }
}