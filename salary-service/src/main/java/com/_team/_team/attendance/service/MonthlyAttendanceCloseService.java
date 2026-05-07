package com._team._team.attendance.service;

import com._team._team.attendance.domain.DailyAttendance;
import com._team._team.attendance.domain.MonthlyAttendanceLedger;
import com._team._team.attendance.domain.OvertimePolicy;
import com._team._team.attendance.domain.enums.AttendanceStatus;
import com._team._team.attendance.domain.enums.ClosureStatus;
import com._team._team.attendance.dto.vo.MonthlyWorkBreakdown;
import com._team._team.attendance.dto.vo.ResolvedSchedule;
import com._team._team.attendance.dto.vo.WorkTimeBreakdown;
import com._team._team.attendance.repository.CompanyHolidayRepository;
import com._team._team.attendance.repository.DailyAttendanceRepository;
import com._team._team.attendance.repository.MonthlyAttendanceLedgerRepository;
import com._team._team.attendance.repository.OvertimePolicyRepository;
import com._team._team.attendance.repository.OvertimeRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 월 장부 마감 서비스
 */
@Slf4j
@Service
public class MonthlyAttendanceCloseService {

    private static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

    private final DailyAttendanceRepository dailyAttendanceRepository;
    private final MonthlyAttendanceLedgerRepository ledgerRepository;
    private final ScheduleResolver scheduleResolver;
    private final WorkTimeClassifier workTimeClassifier;
    private final CompanyHolidayRepository companyHolidayRepository;
    private final OvertimeRequestRepository overtimeRequestRepository;
    private final OvertimePolicyRepository overtimePolicyRepository;

    @Autowired
    public MonthlyAttendanceCloseService(
            DailyAttendanceRepository dailyAttendanceRepository,
            MonthlyAttendanceLedgerRepository ledgerRepository,
            ScheduleResolver scheduleResolver,
            WorkTimeClassifier workTimeClassifier,
            CompanyHolidayRepository companyHolidayRepository,
            OvertimeRequestRepository overtimeRequestRepository,
            OvertimePolicyRepository overtimePolicyRepository) {
        this.dailyAttendanceRepository = dailyAttendanceRepository;
        this.ledgerRepository = ledgerRepository;
        this.scheduleResolver = scheduleResolver;
        this.workTimeClassifier = workTimeClassifier;
        this.companyHolidayRepository = companyHolidayRepository;
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.overtimePolicyRepository = overtimePolicyRepository;
    }

    /**
     * 전월 기준 월 장부 마감
     * 전월의 근태가 있는 모든 회사 대상, 직원별 집계
     */
    public CloseResult processForMonth(YearMonth targetMonth) {
        LocalDate from = targetMonth.atDay(1);
        LocalDate to = targetMonth.atEndOfMonth();

        // 해당 월 근태 있는 회사 목록
        List<UUID> companyIds = dailyAttendanceRepository
                .findDistinctCompanyIdsInRange(from, to);

        int companiesProcessed = 0;
        int ledgersCreated = 0;
        int ledgersSkipped = 0;
        int failed = 0;

        for (UUID companyId : companyIds) {
            try {
                CompanyResult cr = processCompany(companyId, targetMonth);
                companiesProcessed++;
                ledgersCreated += cr.created();
                ledgersSkipped += cr.skipped();
            } catch (Exception e) {
                log.error("[MonthlyClose] 회사 처리 실패 companyId={}, msg={}",
                        companyId, e.getMessage(), e);
                failed++;
            }
        }

        log.info("[MonthlyClose] targetMonth={} 회사={} 생성={} skip={} 실패={}",
                targetMonth, companiesProcessed, ledgersCreated, ledgersSkipped, failed);

        return new CloseResult(companiesProcessed, ledgersCreated, ledgersSkipped, failed);
    }

    // 회사 1곳 외부 노출용 - 회사별 Quartz 트리거에서 호출
    public CloseResult processForCompany(UUID companyId, YearMonth targetMonth) {
        try {
            CompanyResult cr = processCompany(companyId, targetMonth);
            return new CloseResult(1, cr.created(), cr.skipped(), 0);
        } catch (Exception e) {
            log.error("[MonthlyClose] 회사별 처리 실패 companyId={}, msg={}", companyId, e.getMessage(), e);
            return new CloseResult(0, 0, 0, 1);
        }
    }

    // 회사 단위 처리, 미해결 건 있으면 보류, FINALIZED 만 집계
    private CompanyResult processCompany(UUID companyId, YearMonth targetMonth) {
        LocalDate from = targetMonth.atDay(1);
        LocalDate to = targetMonth.atEndOfMonth();

        // 미해결 건 사전 체크
        long unresolved = dailyAttendanceRepository
                .countUnresolvedInMonth(companyId, from, to);
        if (unresolved > 0) {
            log.warn("[MonthlyClose] 미해결 근태 존재 companyId={} unresolved={} → 보류",
                    companyId, unresolved);
            return new CompanyResult(0, 0);
        }

        // FINALIZED 전체 조회 후 직원별 그룹핑
        List<DailyAttendance> finalized = dailyAttendanceRepository
                .findFinalizedInMonth(companyId, from, to);

        Map<UUID, List<DailyAttendance>> byMember = finalized.stream()
                .collect(Collectors.groupingBy(DailyAttendance::getMemberId));

        int created = 0;
        int skipped = 0;

        for (Map.Entry<UUID, List<DailyAttendance>> entry : byMember.entrySet()) {
            try {
                boolean madeNew = upsertLedgerForMember(
                        entry.getKey(), companyId, targetMonth, entry.getValue());
                if (madeNew) created++;
                else skipped++;
            } catch (Exception e) {
                log.error("[MonthlyClose] 직원 집계 실패 memberId={}, msg={}",
                        entry.getKey(), e.getMessage(), e);
            }
        }

        return new CompanyResult(created, skipped);
    }

    /**
     * 직원 1명의 월 집계 장부 생성 또는 갱신
     * 이미 LOCKED 된 장부는 건드리지 않음
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean upsertLedgerForMember(UUID memberId,
                                         UUID companyId,
                                         YearMonth targetMonth,
                                         List<DailyAttendance> dailyList) {

        String ledgerYearMonth = targetMonth.toString();

        // LOCKED 된 기존 장부는 수정 불가
        MonthlyAttendanceLedger existing = ledgerRepository
                .findByMemberIdAndLedgerYearMonth(memberId, ledgerYearMonth)
                .orElse(null);
        if (existing != null && Boolean.TRUE.equals(existing.getIsLocked())) {
            log.info("[MonthlyClose] 잠긴 장부 skip memberId={} yearMonth={}",
                    memberId, ledgerYearMonth);
            return false;
        }

        // 일별 Classifier 실행 후 월 누적
        MonthlyWorkBreakdown totals = aggregateMemberMonth(memberId, companyId, dailyList);

        // 신규 생성 또는 기존 갱신
        boolean created;
        MonthlyAttendanceLedger ledger;
        if (existing == null) {
            ledger = MonthlyAttendanceLedger.builder()
                    .memberId(memberId)
                    .companyId(companyId)
                    .ledgerYearMonth(ledgerYearMonth)
                    .totalWorkedMinutes(totals.totalWorkedMinutes())
                    .regularMinutes(totals.regularMinutes())
                    .overtimeMinutes(totals.overtimeMinutes())
                    .nightMinutes(totals.nightMinutes())
                    .holidayMinutes(totals.holidayMinutes())
                    .leaveMinutes(totals.leaveMinutes())
                    .lateMinutes(totals.lateMinutes())
                    .earlyLeaveMinutes(totals.earlyLeaveMinutes())
                    .absentDays(totals.absentDays())
                    .leaveBreakdownJson(totals.leaveBreakdownJson())
                    .closedAt(LocalDateTime.now())
                    .closedBy(SYSTEM_ACTOR)
                    .isLocked(false)
                    .build();
            ledgerRepository.save(ledger);
            created = true;
        } else {
            // 기존 장부 재마감 (LOCKED 아닐 때만)
            existing.updateAggregates(
                    totals.totalWorkedMinutes(),
                    totals.regularMinutes(),
                    totals.overtimeMinutes(),
                    totals.nightMinutes(),
                    totals.holidayMinutes(),
                    totals.leaveMinutes(),
                    totals.lateMinutes(),
                    totals.earlyLeaveMinutes(),
                    totals.absentDays(),
                    totals.leaveBreakdownJson()
            );
            ledger = existing;
            created = false;
        }

        // 월마감 확정, 일일근태 LOCKED 전이 + 월 장부 lock
        for (DailyAttendance daily : dailyList) {
            if (daily.getClosureStatus() != ClosureStatus.LOCKED) {
                daily.transitionClosure(ClosureStatus.LOCKED);
            }
        }
        ledger.lock();
        log.info("[MonthlyClose] 월 장부 LOCK 완료 memberId={} yearMonth={} dailyCount={}",
                memberId, ledgerYearMonth, dailyList.size());

        return created;
    }

    // 일별 WorkTimeBreakdown 누적해서 월 집계 완성
    private MonthlyWorkBreakdown aggregateMemberMonth(UUID memberId,
                                                      UUID companyId,
                                                      List<DailyAttendance> dailyList) {
        MonthlyWorkBreakdown totals = MonthlyWorkBreakdown.empty();

        for (DailyAttendance daily : dailyList) {
            // 결근은 별도 카운트
            if (daily.getStatus() == AttendanceStatus.ABSENT) {
                totals = totals.incrementAbsent();
                continue;
            }

            WorkTimeBreakdown breakdown = classifyDaily(daily, companyId);
            totals = totals.add(breakdown);
        }

        return totals;
    }

    // 단일 일자 분류, Classifier 호출에 필요한 모든 입력 조회
    private WorkTimeBreakdown classifyDaily(DailyAttendance daily, UUID companyId) {
        ResolvedSchedule schedule = scheduleResolver.resolve(
                daily.getMemberId(), companyId, daily.getAttendanceDate());

        boolean isHoliday = companyHolidayRepository.existsByCompanyIdAndHolidayDate(
                companyId, daily.getAttendanceDate());

        Integer approvedOt = overtimeRequestRepository.sumApprovedMinutes(
                daily.getMemberId(), daily.getAttendanceDate());
        int approvedMinutes = approvedOt == null ? 0 : approvedOt;

        OvertimePolicy otPolicy = overtimePolicyRepository
                .findEffective(companyId, daily.getAttendanceDate())
                .orElseThrow(() -> new IllegalStateException(
                        "OvertimePolicy not found. companyId=" + companyId));

        return workTimeClassifier.classify(
                daily, schedule, isHoliday, approvedMinutes,
                otPolicy.getOvertimeFloorMinutes());
    }

    // 결과 요약 record
    public record CloseResult(int companiesProcessed,
                              int ledgersCreated,
                              int ledgersSkipped,
                              int failed) {}

    private record CompanyResult(int created, int skipped) {}
}