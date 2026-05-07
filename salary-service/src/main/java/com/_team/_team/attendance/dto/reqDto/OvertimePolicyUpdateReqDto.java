package com._team._team.attendance.dto.reqDto;

import com._team._team.attendance.domain.enums.ApprovalMode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import lombok.*;

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

    /** overtimeFloorMinutes 가 15분 또는 30분 만 허용 */
    @AssertTrue(message = "연장근로 인정 단위는 15분 또는 30분만 가능합니다.")
    public boolean isValidOvertimeFloorMinutes() {
        return overtimeFloorMinutes != null
                && (overtimeFloorMinutes == 15 || overtimeFloorMinutes == 30);
    }
}