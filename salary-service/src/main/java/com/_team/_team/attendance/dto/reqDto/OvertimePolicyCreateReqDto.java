package com._team._team.attendance.dto.reqDto;

import com._team._team.attendance.domain.OvertimePolicy;
import com._team._team.attendance.domain.enums.ApprovalMode;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 연장근로 정책 생성 요청 DTO
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class OvertimePolicyCreateReqDto {

    @Positive
    private Integer overtimeFloorMinutes;

    @NotNull
    private ApprovalMode approvalMode;

    @NotNull
    @Positive
    private Integer postApprovalDeadlineHours;

    /** 주 연장근로 상한(분) 법정 720 */
    @Min(value = 0, message = "주 연장근로 한도는 0 이상이어야 합니다.")
    private Integer weeklyOvertimeLimitMinutes;

    /** 주 총 근로시간 상한(분) 법정 3120 */
    @Min(value = 0, message = "주 총 근로시간 한도는 0 이상이어야 합니다.")
    private Integer weeklyTotalLimitMinutes;

    /** 일 연장근로 회사 한도(분) nullable */
    @Positive
    private Integer dailyOvertimeLimitMinutes;

    /** 월 연장근로 회사 한도(분) nullable */
    @Positive
    private Integer monthlyOvertimeLimitMinutes;

    @NotNull
    private Boolean holidayWorkRequiresApproval;

    @NotNull
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    /** overtimeFloorMinutes 는 15분 또는 30분 만 허용 */
    @AssertTrue(message = "연장근로 인정 단위는 15분 또는 30분만 가능합니다.")
    public boolean isValidOvertimeFloorMinutes() {
        return overtimeFloorMinutes != null
                && (overtimeFloorMinutes == 15 || overtimeFloorMinutes == 30);
    }

    public OvertimePolicy toEntity(UUID companyId) {
        return OvertimePolicy.builder()
                .companyId(companyId)
                .overtimeFloorMinutes(overtimeFloorMinutes)
                .approvalMode(approvalMode)
                .postApprovalDeadlineHours(postApprovalDeadlineHours)
                .weeklyOvertimeLimitMinutes(weeklyOvertimeLimitMinutes)
                .weeklyTotalLimitMinutes(weeklyTotalLimitMinutes)
                .dailyOvertimeLimitMinutes(dailyOvertimeLimitMinutes)
                .monthlyOvertimeLimitMinutes(monthlyOvertimeLimitMinutes)
                .holidayWorkRequiresApproval(holidayWorkRequiresApproval)
                .effectiveFrom(effectiveFrom)
                .effectiveTo(effectiveTo)
                .build();
    }
}