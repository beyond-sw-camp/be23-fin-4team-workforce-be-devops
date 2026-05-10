package com._team._team.attendance.domain;

import com._team._team.attendance.domain.enums.ApprovalMode;
import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 연장근로 정책
 * 법정 한도 vs 회사 한도 구분
 */
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Builder
@Getter
@Table(
        indexes = {
                // 특정 일자 유효 정책 조회
                @Index(name = "idx_policy_company_effective",
                        columnList = "companyId, effectiveFrom, effectiveTo")
        }
)
public class OvertimePolicy extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID overtimePolicyId;

    @Column(nullable = false)
    private UUID companyId;

    // 연장근로 인정 단위(분), 15분 또는 30분만 허용 (FLOOR 기반 내림 처리)
    @Column(nullable = false)
    private Integer overtimeFloorMinutes;

    /**
     * 승인 모드
     * 사용자 결정: HYBRID (사전 + 사후 전자결재) -> 사용자 결정 없앴음 -> HYBRID로 고정
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ApprovalMode approvalMode = ApprovalMode.HYBRID;

    /**
     * 사후 신청 마감 시한(시간)
     * 근무 종료 시점으로부터 이 시간 이내에만 사후 신청 가능
     * 사용자 결정: 72 (3일)
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer postApprovalDeadlineHours = 72;

    /**
     * 주 연장근로 상한(분)
     * 근로기준법 제53조: 당사자 합의 시 주 12시간 한도 = 720분
     * 법정 상한이라 회사는 더 낮게 설정 가능, 더 높게는 불가
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer weeklyOvertimeLimitMinutes = 720;

    /**
     * 주 총 근로시간 상한(분), 정규 + 연장 합계
     * 근로기준법 기본 52시간 = 3120분
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer weeklyTotalLimitMinutes = 3120;

    /**
     * 일 연장근로 회사 내부 한도(분)
     * 법정 수치는 없음, 회사 자체 관리용
     */
    private Integer dailyOvertimeLimitMinutes;

    /**
     * 월 연장근로 회사 내부 한도(분)
     * 법정 수치는 없음, 회사 자체 관리용 (예: 월 80시간 = 4800분)
     */
    private Integer monthlyOvertimeLimitMinutes;

    /**
     * 공휴일 근무 시 사전 승인 필요 여부
     * true면 공휴일전에 미리 결재 받아야 함
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean holidayWorkRequiresApproval = true;

    /** 정책 효력 시작일 */
    @Column(nullable = false)
    private LocalDate effectiveFrom;

    /** 정책 효력 종료일 (null이면 현재 적용 중) */
    private LocalDate effectiveTo;

    /** 삭제 여부  */
    @Column(nullable = false, length = 1)
    @Builder.Default
    private String delYn = "N";

    // 정책 효력 종료
    public void close(LocalDate endDate) {
        this.effectiveTo = endDate;
    }

    // 정책 소프트 삭제, 이력 흔적은 보존하되 조회에서 제외
    public void softDelete() {
        this.delYn = "Y";
    }

    // 정책 내용 수정
    public void update(Integer overtimeFloorMinutes,
                       ApprovalMode approvalMode,
                       Integer postApprovalDeadlineHours,
                       Integer weeklyOvertimeLimitMinutes,
                       Integer weeklyTotalLimitMinutes,
                       Integer dailyOvertimeLimitMinutes,
                       Integer monthlyOvertimeLimitMinutes,
                       Boolean holidayWorkRequiresApproval,
                       LocalDate effectiveFrom,
                       LocalDate effectiveTo) {
        if (overtimeFloorMinutes != null) this.overtimeFloorMinutes = overtimeFloorMinutes;
        if (approvalMode != null) this.approvalMode = approvalMode;
        if (postApprovalDeadlineHours != null) this.postApprovalDeadlineHours = postApprovalDeadlineHours;
        if (weeklyOvertimeLimitMinutes != null) this.weeklyOvertimeLimitMinutes = weeklyOvertimeLimitMinutes;
        if (weeklyTotalLimitMinutes != null) this.weeklyTotalLimitMinutes = weeklyTotalLimitMinutes;
        if (dailyOvertimeLimitMinutes != null) this.dailyOvertimeLimitMinutes = dailyOvertimeLimitMinutes;
        if (monthlyOvertimeLimitMinutes != null) this.monthlyOvertimeLimitMinutes = monthlyOvertimeLimitMinutes;
        if (holidayWorkRequiresApproval != null) this.holidayWorkRequiresApproval = holidayWorkRequiresApproval;
        // 적용 기간은 명시적 변경 시에만 갱신 (null 인 경우 기존값 유지)
        if (effectiveFrom != null) this.effectiveFrom = effectiveFrom;
        if (effectiveTo != null) this.effectiveTo = effectiveTo;
    }
}