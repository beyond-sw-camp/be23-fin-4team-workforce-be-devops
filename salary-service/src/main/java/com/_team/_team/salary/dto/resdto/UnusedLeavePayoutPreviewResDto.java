package com._team._team.salary.dto.resdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class UnusedLeavePayoutPreviewResDto {
    private UUID memberId;
    private Long baseSalary;           // 전년 12월 기본급
    private Long dailyWage;            // 1일 통상임금
    private Double unusedDays;         // 미이월 잔여일수
    private Long calculatedAmount;     // 자동 계산된 수당 금액
    private UUID targetPayrollId;      // 반영될 Payroll ID (null이면 Payroll 없음)
    private boolean hasSalary;         // 전년 12월 급여 존재 여부
    private boolean alreadyApplied;    // 이미 반영되었는지 여부
    private String warning;            // 경고 메시지 (UI 에 노란 배지)
}
