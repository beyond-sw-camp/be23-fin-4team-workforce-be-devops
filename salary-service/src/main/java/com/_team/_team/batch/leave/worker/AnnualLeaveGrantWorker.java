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
import java.util.stream.Collectors;
import java.util.function.Function;
import java.time.temporal.ChronoUnit;

/**
 * 법정 연차(연 단위) 부여를 회사(정책) 단위로 수행하는 워커
 * 직원 목록은 {MemberFeignClient}로 조회, 이미 부여된 사원은 저장소 조회로 제외하여 멱등성을 유지
 */
@Component
public class AnnualLeaveGrantWorker {

    /** 주기적으로 flush/clear하여 메모리 및 영속성 컨텍스트를 비움 */
    private static final int BATCH_SIZE = 500;

    private final MemberBalanceRepository memberBalanceRepository;
    private final MemberFeignClient memberFeignClient;
    private final EntityManager entityManager;
    private final MemberLeaveOfAbsenceRepository memberLeaveOfAbsenceRepository;

    @Autowired
    public AnnualLeaveGrantWorker(
            MemberBalanceRepository memberBalanceRepository,
            MemberFeignClient memberFeignClient,
            EntityManager entityManager, MemberLeaveOfAbsenceRepository memberLeaveOfAbsenceRepository) {
        this.memberBalanceRepository = memberBalanceRepository;
        this.memberFeignClient = memberFeignClient;
        this.entityManager = entityManager;
        this.memberLeaveOfAbsenceRepository = memberLeaveOfAbsenceRepository;
    }

    /**
     * 회계연도(FISCAL) 기준 연차 일괄 부여
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnnualLeaveGrantService.BatchResult grantForCompanyFiscal(
            LeavePolicy policy, LocalDate grantDate, LocalDate expirationDate) {

        UUID companyId = policy.getCompanyId();
        AnnualLeaveGrantService.BatchResult batchResult = new AnnualLeaveGrantService.BatchResult();

        Set<UUID> grantedMemberIds = memberBalanceRepository.findGrantedMemberIds(
                companyId, BalanceType.ANNUAL, grantDate, expirationDate);

        // 회사 현재 휴직자 Map (memberId -> LeaveOfAbsence)
        Map<UUID, MemberLeaveOfAbsence> loaMap = loadActiveLeaveOfAbsenceMap(companyId, grantDate);

        List<MemberResDto> memberResDtos = fetchMembers(companyId);
        List<MemberBalance> buffer = new ArrayList<>(BATCH_SIZE);

        for (MemberResDto memberResDto : memberResDtos) {
            if (memberResDto.getJoinDate() == null
                    || memberResDto.getJoinDate().plusYears(1).isAfter(grantDate)) {
                batchResult.skipCount++;
                continue;
            }
            if (grantedMemberIds.contains(memberResDto.getMemberId())) {
                batchResult.skipCount++;
                continue;
            }

            // 휴직 중 skip 대상 체크 (countsAsWorkday=false)
            if (isSkipByLeaveOfAbsence(loaMap, memberResDto.getMemberId())) {
                batchResult.skipCount++;
                continue;
            }

            // 근속 연수 = joinDate ~ grantDate 의 완성 연도
            int tenureYears = (int) ChronoUnit.YEARS.between(memberResDto.getJoinDate(), grantDate);
            // 휴가 정책 공식 사용 정책 컬럼 (defaultAnnualDays / extraDaysPerInterval / extraIntervalYears / maxAnnualDays) 반영
            double grantDays = policy.calculateAnnualDays(tenureYears);

            buffer.add(buildAnnualBalance(companyId, memberResDto.getMemberId(), grantDays, expirationDate));

            batchResult.successCount++;

            if (buffer.size() >= BATCH_SIZE) {
                flushBuffer(buffer);
            }
        }

        if (!buffer.isEmpty()) {
            flushBuffer(buffer);
        }
        return batchResult;
    }
    /**
     * 입사일 기념일(HIRE_DATE) 기준 연차 부여
     * baseDate가 입사 기념일인 직원에게만 부여하며, 윤년 2/29 입사자에 대한 보정을 포함
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnnualLeaveGrantService.BatchResult grantForCompanyHireDate(
            LeavePolicy policy, LocalDate baseDate) {

        UUID companyId = policy.getCompanyId();
        AnnualLeaveGrantService.BatchResult batchResult = new AnnualLeaveGrantService.BatchResult();

        LocalDate grantDate = baseDate;
        LocalDate expirationDate = baseDate.plusYears(1).minusDays(1);

        Set<UUID> grantedMemberIds = memberBalanceRepository.findGrantedMemberIds(
                companyId, BalanceType.ANNUAL, grantDate, expirationDate);

        // 회사 현재 휴직자 Map
        Map<UUID, MemberLeaveOfAbsence> loaMap = loadActiveLeaveOfAbsenceMap(companyId, baseDate);

        List<MemberResDto> memberResDtos = fetchMembers(companyId);

        // 이월 처리, 신규 ANNUAL 부여 직전에 직전 잔여 -> CARRYOVER 변환
        processCarryoverForHireDate(policy, baseDate, grantDate, expirationDate, memberResDtos);

        List<MemberBalance> buffer = new ArrayList<>(BATCH_SIZE);

        for (MemberResDto memberResDto : memberResDtos) {
            LocalDate joinDate = memberResDto.getJoinDate();
            if (joinDate == null) {
                batchResult.skipCount++;
                continue;
            }
            if (!isAnniversary(joinDate, baseDate)) {
                batchResult.skipCount++;
                continue;
            }
            int years = baseDate.getYear() - joinDate.getYear();
            if (years < 1) {
                batchResult.skipCount++;
                continue;
            }
            if (grantedMemberIds.contains(memberResDto.getMemberId())) {
                batchResult.skipCount++;
                continue;
            }

            // 휴직 중 skip 대상 체크
            if (isSkipByLeaveOfAbsence(loaMap, memberResDto.getMemberId())) {
                batchResult.skipCount++;
                continue;
            }

            // 휴가 정책 공식 사용 정책 컬럼 반영
            double grantDays = policy.calculateAnnualDays(years);

            buffer.add(buildAnnualBalance(companyId, memberResDto.getMemberId(), grantDays, expirationDate));
            batchResult.successCount++;

            if (buffer.size() >= BATCH_SIZE) {
                flushBuffer(buffer);
            }
        }
        if (!buffer.isEmpty()) {
            flushBuffer(buffer);
        }
        return batchResult;
    }
    /** 버퍼를 DB에 반영한 뒤 영속성 컨텍스트를 비움 */
    private void flushBuffer(List<MemberBalance> buffer) {
        memberBalanceRepository.saveAll(buffer);
        memberBalanceRepository.flush();
        entityManager.clear();
        buffer.clear();
    }

    /**
     * 휴가부여정책 중 입사기념일 도래자 대상 이월 처리
     * 직전 ANNUAL 만료일 = baseDate - 1 (입사기념일 전날)
     */
    private void processCarryoverForHireDate(
            LeavePolicy policy,
            LocalDate baseDate,
            LocalDate newGrantDate,
            LocalDate newExpirationDate,
            List<MemberResDto> members) {

        if (!"Y".equals(policy.getIsCarryoverYn())) return;
        if (policy.getCarryoverDays() == null || policy.getCarryoverDays() <= 0) return;

        UUID companyId = policy.getCompanyId();
        int carryoverCap = policy.getCarryoverDays();
        boolean requireConsent = "Y".equals(policy.getIsCarryoverConsentYn());

        // 직전 ANNUAL 만료일 = 입사기념일 전날
        LocalDate prevExpiration = baseDate.minusDays(1);

        // 신규 부여 만료일 범위로 이미 CARRYOVER 받은 사람 제외
        Set<UUID> alreadyCarried = memberBalanceRepository.findGrantedMemberIds(
                companyId, BalanceType.CARRYOVER, newGrantDate, newExpirationDate);

        // 직전 연차 잔고 - 만료일 = baseDate - 1 인 것만
        List<MemberBalance> prevAnnuals = memberBalanceRepository
                .findActiveBalancesByCompanyAndTypeAndExpiration(
                        companyId, BalanceType.ANNUAL, prevExpiration, prevExpiration);

        Map<UUID, MemberBalance> prevByMember = prevAnnuals.stream()
                .collect(Collectors.toMap(
                        MemberBalance::getMemberId, b -> b, (a, b) -> a));

        List<MemberBalance> buffer = new ArrayList<>(BATCH_SIZE);

        for (MemberResDto m : members) {
            if (m.getJoinDate() == null) continue;
            // 입사기념일 도래자만 대상
            if (!isAnniversary(m.getJoinDate(), baseDate)) continue;

            UUID memberId = m.getMemberId();
            if (alreadyCarried.contains(memberId)) continue;

            MemberBalance prev = prevByMember.get(memberId);
            if (prev == null || prev.getRemaining() == null || prev.getRemaining() <= 0) continue;
            if (requireConsent && !prev.isCarryoverConsented()) continue;

            double carryDays = Math.min(prev.getRemaining(), carryoverCap);
            if (carryDays <= 0) continue;

            buffer.add(MemberBalance.builder()
                    .memberId(memberId)
                    .companyId(companyId)
                    .balanceType(BalanceType.CARRYOVER)
                    .totalGranted(carryDays)
                    .totalUsed(0.0)
                    .remaining(carryDays)
                    .expirationDate(newExpirationDate)
                    .isUsableYn("Y")
                    .isExpireYn("N")
                    .delYn("N")
                    .build());

            if (buffer.size() >= BATCH_SIZE) {
                flushBuffer(buffer);
            }
        }
        if (!buffer.isEmpty()) {
            flushBuffer(buffer);
        }
    }

    /**
     * 입사일과 기준일이 동일한 기념일인지 판별
     * 윤년 2/29 입사자는 평년에서 2/28을 기념일로 인정
     */
    private boolean isAnniversary(LocalDate joinDate, LocalDate baseDate) {
        if (joinDate.getMonthValue() == baseDate.getMonthValue()
                && joinDate.getDayOfMonth() == baseDate.getDayOfMonth()) {
            return true;
        }
        boolean joinedOnLeapDay =
                joinDate.getMonthValue() == 2 && joinDate.getDayOfMonth() == 29;
        if (joinedOnLeapDay
                && !baseDate.isLeapYear()
                && baseDate.getMonthValue() == 2
                && baseDate.getDayOfMonth() == 28) {
            return true;
        }
        return false;
    }

    /** member-service Feign으로 회사 소속 직원 목록을 조회 */
    private List<MemberResDto> fetchMembers(UUID companyId) {
        ApiResponse<List<MemberResDto>> response = memberFeignClient.getMembersByCompany(companyId);
        if (response == null || response.getData() == null) {
            return Collections.emptyList();
        }
        return response.getData();
    }

    /** 연차 타입 MemberBalance 엔티티를 생성 */
    private MemberBalance buildAnnualBalance(UUID companyId, UUID memberId, Double days, LocalDate expirationDate) {
        return MemberBalance.builder()
                .memberId(memberId)
                .companyId(companyId)
                .balanceType(BalanceType.ANNUAL)
                .totalGranted(days)
                .totalUsed(0.0)
                .remaining(days)
                .expirationDate(expirationDate)
                .isUsableYn("Y")
                .isExpireYn("N")
                .delYn("N")
                .build();
    }

    /**
     * 회사 내 특정 날짜 활성 휴직 리턴
     */
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

    /**
     * 휴직 중이고 연차 미발생 대상이면 true
     * SICK, UNPAID, STUDY 는 연차 발생 안 함 (무급 휴가)
     * MATERNITY, PATERNAL, MILITARY 는 법정 출근 간주로 정상 발생
     */
    private boolean isSkipByLeaveOfAbsence(Map<UUID, MemberLeaveOfAbsence> loaMap,
                                           UUID memberId) {
        MemberLeaveOfAbsence loa = loaMap.get(memberId);
        return loa != null && !loa.getType().countsAsWorkday();
    }

}
