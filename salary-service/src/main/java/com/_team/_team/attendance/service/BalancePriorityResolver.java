package com._team._team.attendance.service;

import com._team._team.attendance.domain.MemberBalance;
import com._team._team.attendance.domain.enums.BalanceType;
import com._team._team.attendance.repository.MemberBalanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


/**
 * 연차류 휴가 차감 시 우선순위 결정
 * MONTHLY -> CARRYOVER -> ANNUAL
 */
@Component
public class BalancePriorityResolver {

    private static final BalanceType[] PRIORITY = {
            BalanceType.MONTHLY,
            BalanceType.CARRYOVER,
            BalanceType.ANNUAL
    };

    private final MemberBalanceRepository memberBalanceRepository;

    @Autowired
    public BalancePriorityResolver(MemberBalanceRepository memberBalanceRepository) {
        this.memberBalanceRepository = memberBalanceRepository;
    }

    /**
     * 우선순위대로 분할 차감 계산
     * MONTHLY 먼저 가능한 만큼, 부족분 CARRYOVER, 부족분 ANNUAL
     */
    public Map<BalanceType, Double> resolveDeductions(UUID companyId, UUID memberId,
                                                      double requestedDays) {
        Map<BalanceType, Double> deductions = new LinkedHashMap<>();
        double remaining = requestedDays;

        for (BalanceType type : PRIORITY) {
            if (remaining <= 0) break;

            // 시드 중복/누적으로 같은 타입 잔고 2건 이상 존재 시 만료일 가장 빠른 1건 우선
            // (NonUniqueResult 방어)
            List<MemberBalance> all = memberBalanceRepository
                    .findAllByCompanyIdAndMemberIdAndBalanceTypeAndDelYn(
                            companyId, memberId, type, "N");
            Optional<MemberBalance> balance = all.stream()
                    .filter(b -> "Y".equals(b.getIsUsableYn()) && "N".equals(b.getIsExpireYn()))
                    .min(Comparator.comparing(
                            MemberBalance::getExpirationDate,
                            Comparator.nullsLast(Comparator.naturalOrder())));

            if (balance.isEmpty()) continue;

            double available = balance.get().getRemaining();
            if (available <= 0) continue;

            double deduct = Math.min(available, remaining);
            deductions.put(type, deduct);
            remaining -= deduct;
        }

        // 잔고 부족
        if (remaining > 0) {
            return null;
        }

        return deductions;
    }
}