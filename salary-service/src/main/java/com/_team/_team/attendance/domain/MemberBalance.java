package com._team._team.attendance.domain;

import com._team._team.attendance.domain.enums.BalanceType;
import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 휴가 잔여 (직원별)
 * - ANNUAL: 당해 연차 / CARRYOVER: 이월 연차 / MONTHLY: 월차
 * - remaining = totalGranted - totalUsed (코드에서 동기화 유지)
 * - 동시 차감 방지: Repository에서 PESSIMISTIC_WRITE 락 사용
 * - expirationDate 지나면 isExpireYn = 'Y'로 배치 처리
 * [동시성 주의]
 * 휴가 승인 2건이 동시에 들어오면 remaining이 마이너스 될 수 있음
 * → MemberBalanceRepository.findWithLock()으로 비관적 락 걸어서 순차 처리
 */
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Getter
@Builder
public class MemberBalance extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID memberBalanceId;

    @Column(nullable = false)
    private UUID memberId;

    @Column(nullable = false)
    private UUID companyId;

    /** 잔여 유형 (ANNUAL: 연차 / CARRYOVER: 이월 연차 / MONTHLY: 월차) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BalanceType balanceType;

    /** 부여 일수 (ex: 15.0일) */
    @Column(nullable = false)
    private Double totalGranted;

    /** 사용 일수 */
    @Builder.Default
    private Double totalUsed = 0.0;

    /** 잔여 일수 = totalGranted - totalUsed */
    @Column(nullable = false)
    private Double remaining;

    /** 만료일 (이 날짜 이후 isExpireYn = 'Y'로 배치 전환) */
    private LocalDate expirationDate;

    /** 사용 가능 여부 (퇴사자 등은 'N') */
    @Column(length = 1, nullable = false)
    @Builder.Default
    private String isUsableYn = "Y";

    /** 만료 여부 (만료일 지나면 배치에서 'Y'로 변경) */
    @Column(length = 1, nullable = false)
    @Builder.Default
    private String isExpireYn = "N";

    /**
     * 이월 동의 여부
     * 직원이 회계연도 종료 직전 "이월 동의" 회신 시 'Y'
     * CarryoverLeaveWorker 가 'Y' 인 잔고만 이월 처리
     */
    @Column(length = 1, nullable = false)
    @Builder.Default
    private String carryoverConsentYn = "N";

    /** 이월 동의 회신 시각 - null 이면 미회신 */
    private LocalDateTime carryoverConsentAt;

    @Column(length = 1, nullable = false)
    @Builder.Default
    private String delYn = "N";

    /**
     * 휴가 차감
     * - 반드시 PESSIMISTIC_WRITE 락 상태에서 호출
     *  days 차감 일수 (반차 = 0.5)
     */
    public void use(double days) {
        this.totalUsed += days;
        this.remaining -= days;
    }
    /**
     * 휴가 복구 (반려/취소 시)
     */
    public void restore(double days) {
        this.totalUsed -= days;
        this.remaining += days;
    }

    /**
     * 월차 누적 부여
     */
    public void addGranted(double days) {
        this.totalGranted += days;
        this.remaining += days;
    }

    /** 만료 배치에서 소멸 처리 */
    public void markExpired() {
        this.isExpireYn = "Y";
        this.isUsableYn = "N";
    }

    /** 직원 이월 동의 회신 - 'Y' + 회신 시각 기록 */
    public void agreeCarryover() {
        this.carryoverConsentYn = "Y";
        this.carryoverConsentAt = LocalDateTime.now();
    }

    /** 이월 동의 철회 - 'N' 으로 복귀 + 시각 초기화 */
    public void revokeCarryoverConsent() {
        this.carryoverConsentYn = "N";
        this.carryoverConsentAt = null;
    }

    public boolean isCarryoverConsented() {
        return "Y".equals(this.carryoverConsentYn);
    }
}
