package com._team._team.batch.leave.worker;

import com._team._team.attendance.repository.LeavePolicyRepository;
import com._team._team.attendance.repository.MemberBalanceRepository;
import com._team._team.attendance.domain.LeavePolicy;
import com._team._team.attendance.domain.MemberBalance;
import com._team._team.attendance.domain.enums.BalanceType;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 이월 연차 자동 생성 배치 워커
 * ===== 실행 시점 =====
 * 매년 1/1 00:00 — ANNUAL 부여/만료 배치보다 먼저 실행
 * - 처리 조건
 * 1. LeavePolicy.isCarryoverYn = 'Y'  인 회사만 대상
 * 2. 전년도 ANNUAL.remaining > 0 인 사원
 */
@Slf4j
@Component
public class CarryoverLeaveWorker {
    private static final int BATCH_SIZE = 500;

    private final LeavePolicyRepository leavePolicyRepository;
    private final MemberBalanceRepository memberBalanceRepository;
    private final EntityManager entityManager;

    @Autowired
    public CarryoverLeaveWorker(
            LeavePolicyRepository leavePolicyRepository,
            MemberBalanceRepository memberBalanceRepository,
            EntityManager entityManager) {
        this.leavePolicyRepository = leavePolicyRepository;
        this.memberBalanceRepository = memberBalanceRepository;
        this.entityManager = entityManager;
    }

    /**
     * 이월 배치 전체 실행 (baseDate = 배치 실행일)
     */
    public void run(LocalDate baseDate) {
        log.info("CarryoverLeaveWorker start baseDate={}", baseDate);

        // 이월 허용 정책만 자동 처리
        List<LeavePolicy> targetPolicies = leavePolicyRepository.findAll().stream()
                .filter(p -> "N".equals(p.getDelYn()))
                .filter(p -> "Y".equals(p.getIsCarryoverYn()))
                .filter(p -> p.getCarryoverDays() != null && p.getCarryoverDays() > 0)
                .toList();
        runForPolicies(targetPolicies, baseDate, null);
    }

    // 회사별 호출, 회사 1곳 정책만 필터
    public void runForCompany(UUID companyId, LocalDate baseDate) {
        List<LeavePolicy> targetPolicies = leavePolicyRepository.findAll().stream()
                .filter(p -> "N".equals(p.getDelYn()))
                .filter(p -> "Y".equals(p.getIsCarryoverYn()))
                .filter(p -> p.getCarryoverDays() != null && p.getCarryoverDays() > 0)
                .filter(p -> companyId.equals(p.getCompanyId()))
                .toList();
        runForPolicies(targetPolicies, baseDate, companyId);
    }

    private void runForPolicies(List<LeavePolicy> targetPolicies, LocalDate baseDate, UUID companyIdLog) {
        int totalSuccess = 0, totalSkip = 0, totalError = 0;
        for (LeavePolicy policy : targetPolicies) {
            try {
                int[] result = carryoverForCompany(policy, baseDate);
                totalSuccess += result[0];
                totalSkip    += result[1];
            } catch (Exception e) {
                log.error("Carryover failed companyId={}", policy.getCompanyId(), e);
                totalError++;
            }
        }
        log.info("CarryoverLeaveWorker end companyIdScope={} success={} skip={} errors={}",
                companyIdLog, totalSuccess, totalSkip, totalError);
    }

    /**
     * 회사 단위 이월 처리
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int[] carryoverForCompany(LeavePolicy policy, LocalDate baseDate) {

        UUID companyId = policy.getCompanyId();
        int carryoverCap = policy.getCarryoverDays();
        boolean requireConsent = "Y".equals(policy.getIsCarryoverConsentYn());

        // 당해년도 이월 대상 기간 (1/1 ~ 12/31)
        LocalDate newYearStart = LocalDate.of(baseDate.getYear(), 1, 1);
        LocalDate newYearEnd   = LocalDate.of(baseDate.getYear(), 12, 31);

        // 전년도 ANNUAL 만료 대상 기간 — 만료일이 전년도 내에 있는 잔여
        LocalDate prevYearStart = LocalDate.of(baseDate.getYear() - 1, 1, 1);
        LocalDate prevYearEnd   = LocalDate.of(baseDate.getYear() - 1, 12, 31);

        // 이미 이월받은 사원 ID (멱등성)
        Set<UUID> alreadyCarried = memberBalanceRepository.findGrantedMemberIds(
                companyId, BalanceType.CARRYOVER, newYearStart, newYearEnd);

        // 전년도 ANNUAL 잔여가 있는 회사 내 사원 조회
        List<MemberBalance> prevAnnuals = memberBalanceRepository
                .findActiveBalancesByCompanyAndTypeAndExpiration(
                        companyId, BalanceType.ANNUAL, prevYearStart, prevYearEnd);

        int successCount = 0;
        int skipCount = 0;
        int skipNoConsent = 0;
        List<MemberBalance> buffer = new ArrayList<>(BATCH_SIZE);

        for (MemberBalance prev : prevAnnuals) {
            if (alreadyCarried.contains(prev.getMemberId())) {
                skipCount++;
                continue;
            }
            if (prev.getRemaining() == null || prev.getRemaining() <= 0) {
                skipCount++;
                continue;
            }

            // 회사 정책이 동의 필요면, 직원 동의 회신한 잔고만 이월 처리
            if (requireConsent && !prev.isCarryoverConsented()) {
                skipNoConsent++;
                continue;
            }

            double carryDays = Math.min(prev.getRemaining(), carryoverCap);
            if (carryDays <= 0) {
                skipCount++;
                continue;
            }

            buffer.add(buildCarryoverBalance(
                    companyId, prev.getMemberId(), carryDays, newYearEnd));
            successCount++;

            if (buffer.size() >= BATCH_SIZE) {
                flushBuffer(buffer);
            }
        }

        if (!buffer.isEmpty()) {
            flushBuffer(buffer);
        }

        log.info("Carryover company={}: success={}, skip={}, skipNoConsent={} (requireConsent={})",
                companyId, successCount, skipCount, skipNoConsent, requireConsent);
        return new int[]{successCount, skipCount};
    }

    private void flushBuffer(List<MemberBalance> buffer) {
        memberBalanceRepository.saveAll(buffer);
        memberBalanceRepository.flush();
        entityManager.clear();
        buffer.clear();
    }

    /** CARRYOVER 타입 MemberBalance 엔티티 생성 */
    private MemberBalance buildCarryoverBalance(
            UUID companyId, UUID memberId, Double days, LocalDate expirationDate) {
        return MemberBalance.builder()
                .memberId(memberId)
                .companyId(companyId)
                .balanceType(BalanceType.CARRYOVER)
                .totalGranted(days)
                .totalUsed(0.0)
                .remaining(days)
                .expirationDate(expirationDate)
                .isUsableYn("Y")
                .isExpireYn("N")
                .delYn("N")
                .build();
    }
}

