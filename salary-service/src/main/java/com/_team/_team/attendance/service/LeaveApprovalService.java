package com._team._team.attendance.service;

import com._team._team.attendance.repository.MemberBalanceRepository;
import com._team._team.attendance.domain.MemberBalance;
import com._team._team.attendance.domain.enums.BalanceType;
import com._team._team.event.LeaveApprovalEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 휴가 결재 이벤트 처리 서비스
 * USE는 승인 시 차감, RESTORE는 반려/취소 시 원복
 */
@Slf4j
@Service
public class LeaveApprovalService {

    private final MemberBalanceRepository memberBalanceRepository;

    @Autowired
    public LeaveApprovalService(MemberBalanceRepository memberBalanceRepository) {
        this.memberBalanceRepository = memberBalanceRepository;
    }

    /**
     * 승인된 휴가 차감, 우선순위 기반 분할 차감
     */
    @Transactional
    public void applyUse(LeaveApprovalEvent event, Map<BalanceType, Double> deductions) {
        if (deductions == null || deductions.isEmpty()) {
            log.warn("[Leave] applyUse 호출됐으나 차감 대상 없음, requestId={}",
                    event.getRequestId());
            return;
        }

        for (Map.Entry<BalanceType, Double> entry : deductions.entrySet()) {
            BalanceType type = entry.getKey();
            Double days = entry.getValue();

            MemberBalance balance = memberBalanceRepository
                    .findWithLock(event.getCompanyId(), event.getMemberId(), type)
                    .orElseThrow(() -> new IllegalStateException(
                            "MemberBalance not found, companyId="
                                    + event.getCompanyId()
                                    + ", memberId=" + event.getMemberId()
                                    + ", type=" + type));

            if (balance.getRemaining() == null || balance.getRemaining() < days) {
                log.warn("[Leave] 차감 시점 잔고 부족, requestId={}, type={}, " +
                                "remaining={}, requested={}",
                        event.getRequestId(), type,
                        balance.getRemaining(), days);
                // 잔고 부족 시 실제 남은 만큼만 차감 or 예외 던지기, 정책 결정 필요
                // 현재는 가능한 만큼만 차감
                days = Math.max(0, balance.getRemaining());
                if (days == 0) continue;
            }

            balance.use(days);
            log.info("[Leave] USE applied. requestId={}, type={}, days={}, remaining={}",
                    event.getRequestId(), type, days, balance.getRemaining());
        }
    }

    /**
     * 반려 시 잔고 복구, 현재 흐름에선 호출 안 됨
     * 승인 전 차감이 없어서 복구할 것이 없음
     */
    @Transactional
    public void applyRestore(LeaveApprovalEvent event, Map<BalanceType, Double> restorations) {
        if (restorations == null || restorations.isEmpty()) return;

        for (Map.Entry<BalanceType, Double> entry : restorations.entrySet()) {
            BalanceType type = entry.getKey();
            Double days = entry.getValue();

            MemberBalance balance = memberBalanceRepository
                    .findWithLock(event.getCompanyId(), event.getMemberId(), type)
                    .orElseThrow(() -> new IllegalStateException(
                            "MemberBalance not found"));

            balance.restore(days);
            log.info("[Leave] RESTORE applied. requestId={}, type={}, days={}, remaining={}",
                    event.getRequestId(), type, days, balance.getRemaining());
        }
    }
}