package com._team._team.attendance.dto.resDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * 직원 월별 OT 누적 vs 회사 월 한도 현황
 * 관리자 [초과 근무 현황] 화면 행 단위
 * 실측 + 승인 둘 다 노출
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class OvertimeUsageResDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private UUID memberId;
    private String name;
    private String organizationName;
    private String jobGradeName;
    private String jobTitleName;
    /** 이번 달 누적 실측 OT 분 (DailyAttendance.overtimeMinutes 합) */
    private Integer actualOvertimeMinutes;
    /** 이번 달 누적 승인 OT 분 (OvertimeRequest APPROVED 합) */
    private Integer approvedMinutes;
    /** 회사 월 한도 분 (OvertimePolicy.monthlyOvertimeLimitMinutes) */
    private Integer fixedLimit;
    /** 사용률 % (actualOvertimeMinutes / fixedLimit * 100) */
    private Double usagePercent;
    /** 한도 초과 분 (없으면 0) */
    private Integer exceedMinutes;
    /** 기간 총 근무시간 분 (정규 + 초과, 퇴근-출근 기반) */
    private Integer totalWorkMinutes;
    /** 기간 분할 단위별 근무 - WEEK 면 7일, MONTH 면 4~5주 */
    private List<Bucket> buckets;

    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    @lombok.Builder
    @lombok.Getter
    public static class Bucket implements Serializable {
        /** WEEK 면 yyyy-MM-dd, MONTH 면 yyyy-Wnn */
        private String key;
        /** 셀 표시 라벨 */
        private String label;
        /** 그 단위 총 근무시간 분 */
        private Integer workedMinutes;
        /** 그 단위 초과근무 분 */
        private Integer overtimeMinutes;
        /** WEEK 모드 한정 - 주말 또는 회사 공휴일 여부 */
        private Boolean holiday;
    }
}
