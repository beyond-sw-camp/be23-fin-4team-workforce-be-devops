package com._team._team.attendance.domain;
import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 월마감 근태 장부
 * 급여 계산의 소스
 */
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Builder
@Getter
@Table(
        uniqueConstraints = {
                // 한 직원 한 달 한 건 (재생성 방지)
                @UniqueConstraint(name = "uk_ledger_member_month",
                        columnNames = {"memberId", "ledger_year_month"})
        },
        indexes = {
                // 회사 월 마감 현황 조회 (관리자 대시보드)
                @Index(name = "idx_ledger_company_month",
                        columnList = "companyId, ledger_year_month"),

                // 잠금 여부 필터 (급여 서비스 조회)
                @Index(name = "idx_ledger_month_locked",
                        columnList = "ledger_year_month, isLocked")
        }
)
public class MonthlyAttendanceLedger extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID ledgerId;

    @Column(nullable = false)
    private UUID memberId;

    @Column(nullable = false)
    private UUID companyId;

    /** 대상 월, "YYYY-MM" 형식 */
    @Column(name = "ledger_year_month", nullable = false, length = 7)
    private String ledgerYearMonth;

    /** 총 순 근무 시간(분), 휴가 차감 반영 */
    @Column(nullable = false)
    @Builder.Default
    private Integer totalWorkedMinutes = 0;

    /** 정규 근무(분) 스케줄 workMinutes 이내 */
    @Column(nullable = false)
    @Builder.Default
    private Integer regularMinutes = 0;

    /** 연장 근무(분) 승인된 OT 만 합산 */
    @Column(nullable = false)
    @Builder.Default
    private Integer overtimeMinutes = 0;

    /** 야간 근무(분) 22:00-06:00 교차분, 가산수당 계산용 */
    @Column(nullable = false)
    @Builder.Default
    private Integer nightMinutes = 0;

    /** 휴일 근무(분) 공휴일, 주말 근무 */
    @Column(nullable = false)
    @Builder.Default
    private Integer holidayMinutes = 0;

    // 휴가, 결근 집계

    /** 휴가로 차감된 근무 시간(분) 연차, 반차, 병가 등 모두 포함 */
    @Column(nullable = false)
    @Builder.Default
    private Integer leaveMinutes = 0;

    /** 지각 시간 합계(분) */
    @Column(nullable = false)
    @Builder.Default
    private Integer lateMinutes = 0;

    /** 조퇴 시간 합계(분) */
    @Column(nullable = false)
    @Builder.Default
    private Integer earlyLeaveMinutes = 0;

    /** 결근 일수 */
    @Column(nullable = false)
    @Builder.Default
    private Integer absentDays = 0;

    /**
     * 휴가 종류별 세부 집계 JSON
     */
    @Column(columnDefinition = "TEXT")
    private String leaveBreakdownJson;

    /** 마감 시각 */
    @Column(nullable = false)
    private LocalDateTime closedAt;

    /** 마감 집행자 (관리자 UUID 또는 시스템) */
    @Column(nullable = false)
    private UUID closedBy;

    /**
     * 잠금 여부
     * 급여 지급 완료 시 true 로 전환, 이후 모든 수정 차단
     * 장부의 불변성을 보장하는 핵심 플래그
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isLocked = false;

    // 상태 전이 메서드

    /**
     * 잠금 처리
     * 급여 지급이 완료된 후 호출
     * 한번 잠기면 풀 수 없음
     */
    public void lock() {
        if (Boolean.TRUE.equals(this.isLocked)) {
            throw new IllegalStateException("이미 확정된 장부입니다.");
        }
        this.isLocked = true;
    }

    /**
     * 집계 값 업데이트
     */
    public void updateAggregates(Integer totalWorkedMinutes,
                                 Integer regularMinutes,
                                 Integer overtimeMinutes,
                                 Integer nightMinutes,
                                 Integer holidayMinutes,
                                 Integer leaveMinutes,
                                 Integer lateMinutes,
                                 Integer earlyLeaveMinutes,
                                 Integer absentDays,
                                 String leaveBreakdownJson) {
        if (Boolean.TRUE.equals(this.isLocked)) {
            throw new IllegalStateException("잠긴 장부는 수정할 수 없습니다.");
        }
        this.totalWorkedMinutes = totalWorkedMinutes;
        this.regularMinutes = regularMinutes;
        this.overtimeMinutes = overtimeMinutes;
        this.nightMinutes = nightMinutes;
        this.holidayMinutes = holidayMinutes;
        this.leaveMinutes = leaveMinutes;
        this.lateMinutes = lateMinutes;
        this.earlyLeaveMinutes = earlyLeaveMinutes;
        this.absentDays = absentDays;
        this.leaveBreakdownJson = leaveBreakdownJson;
    }
}