package com._team._team.batch.leave.worker;

import com._team._team.attendance.domain.MemberLeaveOfAbsence;
import com._team._team.attendance.repository.MemberBalanceRepository;
import com._team._team.attendance.repository.MemberLeaveOfAbsenceRepository;
import com._team._team.attendance.service.AnnualLeaveGrantService;
import com._team._team.attendance.domain.LeavePolicy;
import com._team._team.attendance.domain.MemberBalance;
import com._team._team.attendance.domain.enums.BalanceType;
import com._team._team.dto.ApiResponse;
import com._team._team.salary.feignClients.MemberFeignClient;
import com._team._team.salary.feignClients.dto.MemberResDto;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 근속 1년 미만 직원에게 발생하는 월차(월 단위 누적)를 부여하는 워커
 * 회사별 월차 누적 부여량을 한 번에 조회하여 Map으로 보관한 뒤, Feign으로 조회한 직원 목록과 결합하여 N+1을 방지
 */
@Component
public class MonthlyLeaveGrantWorker {

    /** 배치 flush 단위 */
    private static final int BATCH_SIZE = 500;

    private final MemberBalanceRepository memberBalanceRepository;
    private final MemberFeignClient memberFeignClient;
    private final EntityManager entityManager;
    private final MemberLeaveOfAbsenceRepository memberLeaveOfAbsenceRepository;


    @Autowired
    public MonthlyLeaveGrantWorker(MemberBalanceRepository memberBalanceRepository,
                                   MemberFeignClient memberFeignClient,
                                   EntityManager entityManager, MemberLeaveOfAbsenceRepository memberLeaveOfAbsenceRepository) {
        this.memberBalanceRepository = memberBalanceRepository;
        this.memberFeignClient = memberFeignClient;
        this.entityManager = entityManager;
        this.memberLeaveOfAbsenceRepository = memberLeaveOfAbsenceRepository;
    }

    /**
     * 기준일까지 발생해야 할 월차 개월 수와 DB상 누적 부여량을 비교하여,
     * 부족분만큼 기존 행을 UPDATE하거나 신규 행을 INSERT
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnnualLeaveGrantService.BatchResult grantForCompanyMonthly(LeavePolicy policy, LocalDate baseDate) {

        UUID companyId = policy.getCompanyId();
        AnnualLeaveGrantService.BatchResult batchResult = new AnnualLeaveGrantService.BatchResult();

        List<MemberResDto> members = fetchMembers(companyId);

        Map<UUID, MemberBalance> existingMap = memberBalanceRepository
                .findMonthlyBalancesByCompany(companyId)
                .stream()
                .collect(Collectors.toMap(
                        MemberBalance::getMemberId,
                        Function.identity(),
                        (a, b) -> a
                ));

        // 회사 현재 휴직자 Map
        Map<UUID, MemberLeaveOfAbsence> loaMap = loadActiveLeaveOfAbsenceMap(companyId, baseDate);

        List<MemberBalance> insertBuffer = new ArrayList<>(BATCH_SIZE);

        for (MemberResDto member : members) {
            LocalDate joinDate = member.getJoinDate();

            if (joinDate == null || !baseDate.isBefore(joinDate.plusYears(1))) {
                batchResult.skipCount++;
                continue;
            }

            // 휴직 중 skip 대상 체크
            if (isSkipByLeaveOfAbsence(loaMap, member.getMemberId())) {
                batchResult.skipCount++;
                continue;
            }

            int expectedMonths = calculateExpectedMonthlyLeaves(joinDate, baseDate);
            if (expectedMonths <= 0) {
                batchResult.skipCount++;
                continue;
            }

            MemberBalance existing = existingMap.get(member.getMemberId());

            if (existing != null) {
                double diff = expectedMonths - existing.getTotalGranted();
                if (diff > 0) {
                    existing.addGranted(diff);
                    batchResult.successCount++;
                } else {
                    batchResult.skipCount++;
                }
            } else {
                LocalDate expirationDate = joinDate.plusYears(1).minusDays(1);
                insertBuffer.add(buildMonthlyBalance(
                        companyId, member.getMemberId(),
                        (double) expectedMonths, expirationDate));
                batchResult.successCount++;
            }

            if (insertBuffer.size() >= BATCH_SIZE) {
                flushBuffer(insertBuffer);
            }
        }

        if (!insertBuffer.isEmpty()) {
            flushBuffer(insertBuffer);
        }

        return batchResult;
    }

    private int calculateExpectedMonthlyLeaves(LocalDate joinDate, LocalDate baseDate) {
        int monthsPassed = 0;
        for (int i = 1; i <= 11; i++) {
            if (!baseDate.isBefore(joinDate.plusMonths(i))) {
                monthsPassed = i;
            } else {
                break;
            }
        }
        return monthsPassed;
    }

    private void flushBuffer(List<MemberBalance> buffer) {
        memberBalanceRepository.saveAll(buffer);
        memberBalanceRepository.flush();
        entityManager.clear();
        buffer.clear();
    }

    private List<MemberResDto> fetchMembers(UUID companyId) {
        ApiResponse<List<MemberResDto>> response = memberFeignClient.getMembersByCompany(companyId);
        if (response == null || response.getData() == null) {
            return Collections.emptyList();
        }
        return response.getData();
    }

    private MemberBalance buildMonthlyBalance(UUID companyId, UUID memberId, Double days, LocalDate expirationDate) {
        return MemberBalance.builder()
                .memberId(memberId)
                .companyId(companyId)
                .balanceType(BalanceType.MONTHLY)
                .totalGranted(days)
                .totalUsed(0.0)
                .remaining(days)
                .expirationDate(expirationDate)
                .isUsableYn("Y")
                .isExpireYn("N")
                .delYn("N")
                .build();
    }

    private Map<UUID, MemberLeaveOfAbsence> loadActiveLeaveOfAbsenceMap(UUID companyId,
                                                                        LocalDate date) {
        return memberLeaveOfAbsenceRepository
                .findAllActiveInCompanyOnDate(companyId, date)
                .stream()
                .collect(Collectors.toMap(
                        MemberLeaveOfAbsence::getMemberId,
                        Function.identity(),
                        (a, b) -> a
                ));
    }

    private boolean isSkipByLeaveOfAbsence(Map<UUID, MemberLeaveOfAbsence> loaMap,
                                           UUID memberId) {
        MemberLeaveOfAbsence loa = loaMap.get(memberId);
        return loa != null && !loa.getType().countsAsWorkday();
    }
}
