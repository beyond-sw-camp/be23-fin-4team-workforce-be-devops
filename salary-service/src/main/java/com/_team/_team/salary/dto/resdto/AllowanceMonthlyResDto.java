package com._team._team.salary.dto.resdto;

import com._team._team.salary.domain.enums.PayrollStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 월별 직원 수당 집계 응답 DTO
 * 회사 그 월 정기급여 명세서의 EARNING 라인 (기본급/상여/퇴직 제외) 만 모음
 * 회사 공통 + 개인 차등 둘 다 포함
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class AllowanceMonthlyResDto {

    private UUID memberId;
    private PayrollStatus payrollStatus;
    private List<Line> items;
    private Long totalAmount;

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Getter
    public static class Line {
        /** PayrollItem 이번 달 명세서에서만 빼기 */
        private UUID payrollItemId;
        /** MemberAllowance PK */
        private UUID memberAllowanceId;
        /** 화면에서 색상 분기 */
        private LocalDate effectiveTo;
        private String itemName;
        private Long amount;
        /** 기본 금액 고정 항목 여부 - 직원별 차등 불가 */
        private Boolean isCommon;
        /** 비과세 여부 */
        private Boolean isTaxFree;
    }
}
