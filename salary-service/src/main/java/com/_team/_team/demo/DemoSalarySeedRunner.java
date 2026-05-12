package com._team._team.demo;

import com._team._team.attendance.domain.AttendanceLog;
import com._team._team.attendance.domain.CompanyHoliday;
import com._team._team.attendance.domain.CompanyLeaveType;
import com._team._team.attendance.domain.DailyAttendance;
import com._team._team.attendance.domain.LeavePolicy;
import com._team._team.attendance.domain.LeaveRequest;
import com._team._team.attendance.domain.MemberBalance;
import com._team._team.attendance.domain.MemberScheduleSelection;
import com._team._team.attendance.domain.MonthlyAttendanceLedger;
import com._team._team.attendance.domain.OvertimePolicy;
import com._team._team.attendance.domain.WorkSchedule;
import com._team._team.attendance.domain.FlexibleTimeSlot;
import com._team._team.attendance.domain.WorkTripDetail;
import com._team._team.attendance.domain.enums.AccrualBase;
import com._team._team.attendance.domain.enums.ApprovalMode;
import com._team._team.attendance.domain.enums.AttendanceStatus;
import com._team._team.attendance.domain.enums.BalanceType;
import com._team._team.attendance.domain.enums.ClosureStatus;
import com._team._team.attendance.domain.enums.EventType;
import com._team._team.attendance.domain.enums.LeaveApprovalStatus;
import com._team._team.attendance.domain.enums.LeaveInitiator;
import com._team._team.attendance.domain.enums.ScheduleApprovalStatus;
import com._team._team.attendance.domain.enums.SourceType;
import com._team._team.attendance.domain.enums.WorkTripType;
import com._team._team.attendance.domain.enums.WorkType;
import com._team._team.attendance.repository.AttendanceLogRepository;
import com._team._team.batch.leave.worker.LeaveGrantWorker;
import com._team._team.attendance.repository.CompanyHolidayRepository;
import com._team._team.attendance.repository.CompanyLeaveTypeRepository;
import com._team._team.attendance.repository.DailyAttendanceRepository;
import com._team._team.attendance.repository.LeavePolicyRepository;
import com._team._team.attendance.repository.LeaveRequestRepository;
import com._team._team.attendance.service.CompanyHolidayService;
import com._team._team.attendance.service.CompanyLeaveTypeService;
import com._team._team.attendance.repository.MemberBalanceRepository;
import com._team._team.attendance.repository.MemberScheduleSelectionRepository;
import com._team._team.attendance.repository.MonthlyAttendanceLedgerRepository;
import com._team._team.attendance.repository.OvertimePolicyRepository;
import com._team._team.attendance.repository.WorkScheduleRepository;
import com._team._team.attendance.repository.FlexibleTimeSlotRepository;
import com._team._team.attendance.repository.WorkTripDetailRepository;
import com._team._team.attendance.domain.OvertimeRequest;
import com._team._team.attendance.domain.enums.OvertimeApprovalStatus;
import com._team._team.attendance.domain.enums.OvertimeRequestType;
import com._team._team.attendance.repository.OvertimeRequestRepository;
import com._team._team.attendance.domain.LeavePromotionLog;
import com._team._team.attendance.domain.enums.PromotionLogStatus;
import com._team._team.attendance.domain.enums.PromotionStage;
import com._team._team.attendance.repository.LeavePromotionLogRepository;
import com._team._team.dto.ApiResponse;
import com._team._team.salary.domain.BonusPolicy;
import com._team._team.salary.domain.MemberAllowance;
import com._team._team.salary.domain.PayGradeTable;
import com._team._team.salary.domain.RetirementPolicy;
import com._team._team.salary.domain.Salary;
import com._team._team.salary.domain.SalaryItemTemplate;
import com._team._team.salary.domain.SalaryPolicy;
import com._team._team.salary.domain.enums.AllowanceApprovalStatus;
import com._team._team.salary.domain.enums.BonusEligibilityScope;
import com._team._team.salary.domain.enums.HolidayBonusType;
import com._team._team.salary.domain.enums.PayCycleType;
import com._team._team.salary.domain.enums.PayDayShiftRule;
import com._team._team.salary.domain.enums.PayrollType;
import com._team._team.salary.domain.enums.ProrationMethod;
import com._team._team.salary.domain.enums.RetirementType;
import com._team._team.salary.domain.enums.TaxReductionType;
import com._team._team.salary.domain.enums.WageSystemType;
import com._team._team.salary.dto.reqdto.PayrollCreateReqDto;
import com._team._team.salary.dto.reqdto.PayrollItemCreateReqDto;
import com._team._team.salary.feignClients.MemberFeignClient;
import com._team._team.salary.feignClients.dto.CompanyInfoResDto;
import com._team._team.salary.feignClients.dto.MemberResDto;
import com._team._team.salary.repository.BonusPolicyRepository;
import com._team._team.salary.repository.MemberAllowanceRepository;
import com._team._team.salary.repository.PayGradeTableRepository;
import com._team._team.salary.repository.PayrollRepository;
import com._team._team.salary.repository.RetirementPolicyRepository;
import com._team._team.salary.repository.SalaryItemTemplateRepository;
import com._team._team.salary.repository.SalaryPolicyRepository;
import com._team._team.salary.repository.SalaryRepository;
import com._team._team.salary.service.PayrollService;
import com._team._team.salary.service.SalaryItemTemplateService;
import com._team._team.salary.service.SimplifiedTaxTableService;
import com._team._team.salary.service.TaxRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 데모용 시드 러너 - seed.demo.enabled=true 일 때만 동작 -> 말 그대로 데모 데이터 -> 온갖 테스트용
 *  1) 시스템 공통 시드 (멱등): TaxRate (4대보험·소득세·지방세 비율) + SimplifiedTaxTable (간이세액표 엑셀)
 *  2) 회사 4 (도메인 demo-current): 당월/연봉제 정책 + 표준 항목 + 퇴직정책 + 연장근로정책
 *  3) 회사 5 (도메인 demo-prev): 전월/호봉제 정책 + 호봉표 + 표준 항목 + 퇴직정책 + 연장근로정책
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "seed.demo.enabled", havingValue = "true")
@Order(100)
@RequiredArgsConstructor
public class DemoSalarySeedRunner implements ApplicationRunner {

    private final MemberFeignClient memberFeignClient;
    private final SalaryPolicyRepository salaryPolicyRepository;
    private final RetirementPolicyRepository retirementPolicyRepository;
    private final OvertimePolicyRepository overtimePolicyRepository;
    private final PayGradeTableRepository payGradeTableRepository;
    private final SalaryRepository salaryRepository;
    private final MemberAllowanceRepository memberAllowanceRepository;
    private final SalaryItemTemplateRepository salaryItemTemplateRepository;
    private final SalaryItemTemplateService salaryItemTemplateService;
    private final TaxRateService taxRateService;
    private final SimplifiedTaxTableService simplifiedTaxTableService;
    private final MonthlyAttendanceLedgerRepository ledgerRepository;
    private final PayrollRepository payrollRepository;
    private final PayrollService payrollService;
    private final MemberBalanceRepository memberBalanceRepository;
    private final BonusPolicyRepository bonusPolicyRepository;
    private final DailyAttendanceRepository dailyAttendanceRepository;
    private final CompanyHolidayRepository companyHolidayRepository;
    private final CompanyHolidayService companyHolidayService;
    private final AttendanceLogRepository attendanceLogRepository;
    private final WorkTripDetailRepository workTripDetailRepository;
    private final OvertimeRequestRepository overtimeRequestRepository;
    private final LeavePromotionLogRepository leavePromotionLogRepository;
    private final LeavePolicyRepository leavePolicyRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final CompanyLeaveTypeRepository companyLeaveTypeRepository;
    private final CompanyLeaveTypeService companyLeaveTypeService;
    private final WorkScheduleRepository workScheduleRepository;
    private final FlexibleTimeSlotRepository flexibleTimeSlotRepository;
    private final MemberScheduleSelectionRepository memberScheduleSelectionRepository;
    // 시드용 ANNUAL 부여 - 정책 기반으로 LeaveGrantWorker 가 직접 부여 (seedMemberBalances 대체)
    private final LeaveGrantWorker leaveGrantWorker;

    /** AUTO 부여 (관리자 즉시 부여) 표시용 - 시드 데이터의 시스템 부여 표식 */
    private static final UUID SYSTEM_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final String DOMAIN_CURRENT = "digitalTeck";
    private static final String DOMAIN_PREV = "solution";
    private static final String DOMAIN_3 = "creation";
    private static final String TAX_TABLE_PATTERN = "classpath*:data/simplified-tax-table-*.xlsx";
    private static final Pattern YEAR_REGEX = Pattern.compile("simplified-tax-table-(\\d{4})\\.xlsx");

    @Override
    public void run(ApplicationArguments args) {
        log.info("[DEMO-SEED] 시작");

        // 1) 시스템 공통 시드 (회사 무관, 한 번만)
        try {
            seedTaxRates();
        } catch (Exception e) {
            log.warn("[DEMO-SEED] TaxRate 시드 실패 (계속 진행) - {}", e.getMessage());
        }
        try {
            seedSimplifiedTaxTables();
        } catch (Exception e) {
            log.warn("[DEMO-SEED] SimplifiedTaxTable 시드 실패 (계속 진행) - {}", e.getMessage());
        }

        // 2) 회사 4 (당월/연봉제)
        try {
            seedCompany(DOMAIN_CURRENT, false /* usePayGrade */, PayCycleType.CURRENT_MONTH, 25);
        } catch (Exception e) {
            log.error("[DEMO-SEED] 회사 4 시드 실패 - {}", e.getMessage(), e);
        }

        // 3) 회사 5 (전월/호봉제)
        try {
            seedCompany(DOMAIN_PREV, true, PayCycleType.PREVIOUS_MONTH, 10);
        } catch (Exception e) {
            log.error("[DEMO-SEED] 회사 5 시드 실패 - {}", e.getMessage(), e);
        }

        // 4) 회사 6 - demo-3 (당월/연봉제)
        try {
            seedCompany(DOMAIN_3, false, PayCycleType.CURRENT_MONTH, 25);
        } catch (Exception e) {
            log.error("[DEMO-SEED] 회사 6 시드 실패 - {}", e.getMessage(), e);
        }

        log.info("[DEMO-SEED] 정책 시드 완료. 직원/Payroll/Ledger 시드는 후속 Phase 에서 진행");
    }

    /**
     * TaxRate 시스템 공통 시드 - applyYear 별 4대보험·소득세·지방세 비율
     * TaxRateService.initializeDefaults 자체 멱등 처리
     */
    private void seedTaxRates() {
        for (int year = 2024; year <= 2026; year++) {
            TaxRateService.SeedResult res = taxRateService.initializeDefaults(year);
            log.info("[DEMO-SEED] TaxRate {} 시드 - inserted={} skipped={}",
                    res.applyYear(), res.inserted(), res.skipped());
        }
    }

    /**
     * SimplifiedTaxTable 시스템 공통 시드 - resources/data/simplified-tax-table-{year}.xlsx 자동 업로드
     * 파일 없으면 skip + 경고만 (소득세 0원 fallback 동작)
     */
    private void seedSimplifiedTaxTables() throws IOException {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(TAX_TABLE_PATTERN);
        if (resources.length == 0) {
            log.warn("[DEMO-SEED] SimplifiedTaxTable 엑셀 없음 - {} 위치 확인. 소득세 0원 fallback",
                    TAX_TABLE_PATTERN);
            return;
        }
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null) continue;
            Matcher m = YEAR_REGEX.matcher(filename);
            if (!m.find()) {
                log.warn("[DEMO-SEED] 파일명에서 year 추출 실패 - {} (패턴: simplified-tax-table-YYYY.xlsx)", filename);
                continue;
            }
            int year = Integer.parseInt(m.group(1));
            // 이미 등록된 연도면 skip
            if (simplifiedTaxTableService.countByYear(year) > 0) {
                log.info("[DEMO-SEED] SimplifiedTaxTable {} 이미 등록됨 skip", year);
                continue;
            }
            try (InputStream is = resource.getInputStream()) {
                byte[] bytes = is.readAllBytes();
                MultipartFile mock = new InMemoryMultipartFile(
                        "file", filename,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        bytes);
                int inserted = simplifiedTaxTableService.uploadTaxTable(year, mock);
                log.info("[DEMO-SEED] SimplifiedTaxTable {} 업로드 완료 - {} 건", year, inserted);
            }
        }
    }

    /**
     * 클래스패스 리소스를 MultipartFile 로 wrap 하기 위한 in-memory 구현
     * (spring-test 의 MockMultipartFile 은 testImplementation 이라 main 에서 사용 불가)
     */
    private record InMemoryMultipartFile(
            String name, String originalFilename, String contentType, byte[] content
    ) implements MultipartFile {
        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content == null || content.length == 0; }
        @Override public long getSize() { return content == null ? 0 : content.length; }
        @Override public byte[] getBytes() { return content == null ? new byte[0] : content; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(getBytes()); }
        @Override public void transferTo(File dest) throws IOException {
            try (FileOutputStream fos = new FileOutputStream(dest)) {
                fos.write(getBytes());
            }
        }
    }

    /**
     * 회사별 정책 시드 - 멱등 처리 (이미 있으면 skip)
     */
    public void seedCompany(String domain, boolean usePayGrade,
                            PayCycleType cycleType, int payDay) {
        // 회사별 연차 정책: 서울/강남 = 회계년도+촉진ON, 판교 = 입사일+촉진OFF
        AccrualBase accrualBase = "creation".equals(domain) ? AccrualBase.HIRE_DATE : AccrualBase.FISCAL;
        boolean usePromotion = !"creation".equals(domain);
        seedCompanyInternal(domain, usePayGrade, cycleType, payDay, accrualBase, usePromotion);
    }

    private void seedCompanyInternal(String domain, boolean usePayGrade,
                                     PayCycleType cycleType, int payDay,
                                     AccrualBase accrualBase, boolean usePromotion) {
        // 1) 회사 도메인 -> 회사 ID
        CompanyInfoResDto company;
        try {
            company = memberFeignClient.getCompanyByDomain(domain).getData();
        } catch (Exception e) {
            log.warn("[DEMO-SEED] 회사 조회 실패 domain={} - {}", domain, e.getMessage());
            return;
        }
        if (company == null || company.getCompanyId() == null) {
            log.warn("[DEMO-SEED] 회사 없음 domain={}", domain);
            return;
        }
        UUID companyId = company.getCompanyId();
        log.info("[DEMO-SEED] 회사 정책 시드 시작 - {} ({})", company.getCompanyName(), companyId);

        // 시드 적용시작일 월 1일로 강제
        LocalDate effectiveFrom = LocalDate.now().minusYears(3).withDayOfMonth(1);

        // 2~3) SalaryPolicy 멱등 - 이미 있으면 정책 부분만 skip, 후속 시드(Salary/MemberBalance/Payroll)는 계속 진행
        SalaryPolicy salaryPolicy = salaryPolicyRepository
                .findActivePolicies(companyId, LocalDate.now())
                .stream().findFirst()
                .orElse(null);
        if (salaryPolicy != null) {
            log.info("[DEMO-SEED] 활성 SalaryPolicy 이미 존재 - 정책 시드 skip companyId={}", companyId);
        } else {
            salaryPolicy = SalaryPolicy.builder()
                    .companyId(companyId)
                    .policyName(usePayGrade ? "데모 호봉제 정책" : "데모 연봉제 정책")
                    .payDay(payDay)
                    .payDayShiftRule(PayDayShiftRule.BEFORE)
                    .payCycleType(cycleType)
                    .usePayGradeYn(usePayGrade ? "Y" : "N")
                    .wageSystemType(WageSystemType.NON_COMPREHENSIVE)
                    .fixedOvertimeMinutes(0)
                    .monthlyOrdinaryHours(209)
                    .prorationMethod(ProrationMethod.DAYS_IN_MONTH)
                    .effectiveFrom(effectiveFrom)
                    .build();
            salaryPolicyRepository.save(salaryPolicy);
            log.info("[DEMO-SEED] SalaryPolicy 시드 완료 cycle={} payDay={} usePayGrade={}",
                    cycleType, payDay, usePayGrade);

            // 4) RetirementPolicy - LEGAL 기본
            RetirementPolicy retirementPolicy = RetirementPolicy.builder()
                    .companyId(companyId)
                    .retirementType(RetirementType.LEGAL)
                    .effectiveFrom(effectiveFrom)
                    .memo("데모 시드 - 법정 퇴직금")
                    .build();
            retirementPolicyRepository.save(retirementPolicy);
            log.info("[DEMO-SEED] RetirementPolicy LEGAL 시드 완료");

            // 5) OvertimePolicy - seedOvertimePolicy() 로 위임 (자체 멱등)
            seedOvertimePolicy(companyId, effectiveFrom);

            // 6) SalaryItemTemplate (initializeDefaults - 자체 멱등)
            SalaryItemTemplateService.SeedResult tplResult = salaryItemTemplateService.initializeDefaults(companyId);
            log.info("[DEMO-SEED] SalaryItemTemplate 시드 - {}", tplResult.message());

            // 7) PayGradeTable - 호봉제 회사만
            if (usePayGrade) {
                seedPayGradeTable(companyId, effectiveFrom);
            }

            // 7-1) BonusPolicy - 회사별 보너스 룰 (정기상여 + 성과급 + 명절상여)
            seedBonusPolicy(companyId, usePayGrade, effectiveFrom);
        }

        // 7-2) OvertimePolicy 자체 멱등 - SalaryPolicy 가 이미 있어도 OvertimePolicy 누락 시 보강
        seedOvertimePolicy(companyId, effectiveFrom);

        // 8) 직원별 Salary + MemberAllowance 시드 (자체 멱등)
        seedEmployees(companyId, salaryPolicy.getSalaryPolicyId(), usePayGrade);

        // 8-1) 직원별 ANNUAL 연차 부여 (historical) - 입사년도부터 매년 1/1 + 입사 기념일마다 호출
        // 회계연도 회사: 매년 1/1 부여 / 입사일 회사: 입사 기념일 부여 (LeaveGrantWorker 정책 자동 분기)
        seedHistoricalLeaveGrants(companyId);

        // 8-2) 회사 공휴일 - 법정 공휴일 자동 import (daily 시드에서 휴일 skip 위해 선행)
        seedCompanyHolidays(companyId);

        // 8-2-1) LeavePolicy + CompanyLeaveType 기본 - LeaveRequest 시드 선행
        seedLeavePolicy(companyId, effectiveFrom, accrualBase, usePromotion);
        seedCompanyLeaveTypes(companyId);

        // 8-2-2) WorkSchedule - 회사 근무 스케줄
        // demo-current (usePayGrade=false): FIXED 1개
        // demo-prev    (usePayGrade=true) : FLEXIBLE 1개 + EARLY/STANDARD/LATE 슬롯 3개
        seedWorkSchedule(companyId, effectiveFrom, usePayGrade);

        // 8-3) 직원별 일별 출퇴근 시드 (입사일 ~ 어제까지, 평일/공휴일 제외)
        seedDailyAttendance(companyId);

        // 9) 매월 MonthlyAttendanceLedger + Payroll 시드 (자체 멱등)
        seedMonthlyLedgersAndPayrolls(companyId, cycleType, payDay);

        // 10) 초과근무 신청 이력 (APPROVED, 결재 우회) - 5명 분배
        seedOvertimeRequests(companyId);

        // 11) 휴가 신청 이력 추가 - 직원당 매월 1건 (APPROVED)
        seedAdditionalLeaveRequests(companyId);

        // 12) 연차사용촉진 알림 + 회신 + 강제지정 시나리오 (촉진 ON 회사만)
        if (usePromotion) {
            seedLeavePromotionLogs(companyId);
        }

        // 13) 조퇴 / 근태정정 / 수당변경 이력 (결재 우회, 도메인 직접 update/insert)
        seedEarlyLeaveAndCorrections(companyId);
        seedAllowanceChanges(companyId);

        log.info("[DEMO-SEED] 회사 시드 완료 - {}", company.getCompanyName());
    }

    /**
     * 직원별 입사일 ~ 지난달까지 매월 MonthlyAttendanceLedger 와 Payroll 시드
     *
     * Ledger:
     *  - regularMinutes = 209h (12540분) 표준
     *  - 매 3개월마다 overtime 5h 추가 (퇴직금 12개월 환산 검증용)
     *  - isLocked = false (Payroll 자동 산정 후에도 unlock 유지 - 시드 검증 편의)
     *
     * Payroll:
     *  - PayrollService.createPayroll 호출 -> 자동 항목 (4대보험·소득세·통상임금·OT) 정확 산출
     *  - 지급일 = SalaryPolicy.payCycleType 따라 산출 (CURRENT/PREVIOUS)
     *  - 미래 지급일은 skip (오늘 이후)
     *  - 멱등 - 이미 있으면 skip
     */
    private void seedMonthlyLedgersAndPayrolls(UUID companyId, PayCycleType cycleType, int payDay) {
        ApiResponse<List<MemberResDto>> apiRes;
        try {
            apiRes = memberFeignClient.getMembersByCompany(companyId);
        } catch (Exception e) {
            log.warn("[DEMO-SEED] 직원 list 조회 실패 - {}", e.getMessage());
            return;
        }
        List<MemberResDto> members = apiRes != null ? apiRes.getData() : List.of();
        if (members.isEmpty()) return;

        // 입사일 순 정렬 - idx 기반 패턴 분기용
        List<MemberResDto> sorted = members.stream()
                .filter(m -> m.getJoinDate() != null)
                .sorted((a, b) -> a.getJoinDate().compareTo(b.getJoinDate()))
                .toList();

        // 미사용 연차 수당 템플릿 미리 조회
        UUID unusedLeaveTplId = findTemplateId(companyId, "미사용 연차 수당");

        LocalDate today = LocalDate.now();
        YearMonth currentYm = YearMonth.from(today);
        int totalLedgers = 0;
        int totalPayrolls = 0;
        int skipped = 0;
        int failed = 0;
        int unusedLeaveSeeded = 0;

        for (int i = 0; i < sorted.size(); i++) {
            MemberResDto member = sorted.get(i);
            if (member.getJoinDate() == null) continue;
            // 급여(Payroll) 시작월 결정 - 입사일이 그 달 payDay 이전이면 입사월 포함, 이후면 다음 달부터
            // 예: 25일 월급, 9/15 입사 -> 9월 정산 시드. 9/27 입사 -> 10월부터 시드
            YearMonth joinYm = YearMonth.from(member.getJoinDate());
            LocalDate firstPayDate = resolvePayDate(joinYm, cycleType, payDay);
            YearMonth start = !member.getJoinDate().isAfter(firstPayDate)
                    ? joinYm
                    : joinYm.plusMonths(1);
            YearMonth end = currentYm.minusMonths(1);

            for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
                // 1) Ledger 시드
                String ymStr = ym.toString();
                if (ledgerRepository.findByMemberIdAndLedgerYearMonth(member.getMemberId(), ymStr).isEmpty()) {
                    seedLedger(member.getMemberId(), companyId, ym);
                    totalLedgers++;
                }

                // 2) Payroll 시드 - 회사 cycleType 따라 지급일 산출
                LocalDate payDate = resolvePayDate(ym, cycleType, payDay);
                if (payDate.isAfter(today)) continue; // 미래 지급일 skip

                if (payrollRepository.findByCompanyIdAndMemberIdAndPayrollYearMonthDay(
                        companyId, member.getMemberId(), payDate).isPresent()) {
                    skipped++;
                    continue;
                }

                try {
                    var created = payrollService.createPayroll(companyId,
                            PayrollCreateReqDto.builder()
                                    .memberId(member.getMemberId())
                                    .payrollYearMonthDay(payDate)
                                    .payrollType(PayrollType.REGULAR_MONTHLY)
                                    .build());
                    totalPayrolls++;

                    // 12월 정산 + 1년 이상 재직 + idx 패턴 = 미사용 연차 수당 라인 추가
                    // confirm/pay 이전에 추가해야 modifiable
                    if (ym.getMonthValue() == 12 && unusedLeaveTplId != null
                            && i % 3 == 0
                            && member.getJoinDate().plusYears(1).isBefore(ym.atDay(1))) {
                        if (seedUnusedLeavePay(companyId, member.getMemberId(),
                                created.getPayrollId(), i)) {
                            unusedLeaveSeeded++;
                        }
                    }

                    // 과거 시드 데이터 의미 살리기 - 입사일 ~ 지난달까지 모두 PAID
                    // 시드 데이터 의미상 실제 지급일(payrollYearMonthDay) 로 덮어씀
                    final LocalDate paidDate = payDate;
                    payrollRepository.findById(created.getPayrollId()).ifPresent(p -> {
                        p.confirm();
                        p.pay();
                        p.overridePaidAt(paidDate);
                        payrollRepository.save(p);
                    });
                } catch (Exception e) {
                    failed++;
                    log.warn("[DEMO-SEED] Payroll 시드 실패 memberId={} payDate={} - {}",
                            member.getMemberId(), payDate, e.getMessage());
                }
            }
        }
        log.info("[DEMO-SEED] 월별 시드 결과 - Ledger 신규 {} / Payroll 신규 {} / skip {} / fail {} / 미사용연차수당 {}",
                totalLedgers, totalPayrolls, skipped, failed, unusedLeaveSeeded);
    }

    /**
     * 미사용 연차 수당 PayrollItem 시드
     */
    private boolean seedUnusedLeavePay(UUID companyId, UUID memberId, UUID payrollId, int i) {
        UUID tplId = findTemplateId(companyId, "미사용 연차 수당");
        if (tplId == null) return false;
        Salary activeSalary = salaryRepository
                .findActiveSalary(memberId, companyId, LocalDate.now()).orElse(null);
        if (activeSalary == null) return false;
        int[] unusedDaysByIdx = { 7, 5, 8, 6, 4, 3 };
        int unusedDays = unusedDaysByIdx[i % unusedDaysByIdx.length];
        long dailyWage = activeSalary.getBaseSalary() / 21; // 월 21일 기준 일급
        long raw = dailyWage * unusedDays;
        long amount = ((raw + 9999) / 10000) * 10000; // 만원 단위 반올림
        try {
            payrollService.createPayrollItem(companyId, payrollId,
                    PayrollItemCreateReqDto.builder()
                            .salaryItemTemplateId(tplId)
                            .amount(amount)
                            .build());
            return true;
        } catch (Exception e) {
            log.warn("[DEMO-SEED] 미사용 연차 수당 시드 실패 memberId={} - {}", memberId, e.getMessage());
            return false;
        }
    }

    /**
     * 회사 cycleType 따라 정산 대상 월 -> 지급일 산출
     * - CURRENT_MONTH: 해당 월 payDay
     * - PREVIOUS_MONTH: 다음 월 payDay (말일 경계 보정)
     */
    private LocalDate resolvePayDate(YearMonth targetYm, PayCycleType cycleType, int payDay) {
        YearMonth payMonth = (cycleType == PayCycleType.PREVIOUS_MONTH)
                ? targetYm.plusMonths(1)
                : targetYm;
        int day = Math.min(payDay, payMonth.lengthOfMonth());
        return payMonth.atDay(day);
    }

    /**
     * 초과근무 신청 이력 시드
     * 분배: 팀장/인사관리자 제외한 직원 중 5명에게
     *  - 2명 월 10건 / 2명 월 5건 / 1명 월 2건
     * 기간: 입사일 ~ 지난달까지 매월 평일 분산
     * 멱등: 회사에 OvertimeRequest 이미 있으면 skip
     */
    private void seedOvertimeRequests(UUID companyId) {
        long existing = overtimeRequestRepository.count();
        // 회사 단위 정확 멱등 체크가 어려우므로 간단히: 해당 회사 첫 직원에게 이미 있는지 확인
        ApiResponse<List<MemberResDto>> apiRes;
        try {
            apiRes = memberFeignClient.getMembersByCompany(companyId);
        } catch (Exception e) {
            log.warn("[DEMO-SEED] OT 시드 - 직원 조회 실패 companyId={} - {}", companyId, e.getMessage());
            return;
        }
        List<MemberResDto> members = apiRes != null ? apiRes.getData() : null;
        if (members == null || members.isEmpty()) return;

        // 팀장 / 인사관리자(인사팀의 파트장) 제외
        List<MemberResDto> targets = members.stream()
                .filter(m -> !"팀장".equals(m.getJobTitleName()))
                .filter(m -> !("인사팀".equals(m.getOrganizationName())
                        && "파트장".equals(m.getJobTitleName())))
                .filter(m -> m.getJoinDate() != null)
                .sorted((a, b) -> a.getJoinDate().compareTo(b.getJoinDate()))
                .toList();
        if (targets.isEmpty()) return;

        // 회사당 5명 선택 + 월 신청 건수 분배
        int[] perMonthByIdx = { 10, 10, 5, 5, 2 };
        int picked = Math.min(perMonthByIdx.length, targets.size());

        // 회사 내 기존 OT 시드 확인 (첫 대상 직원의 데이터로 멱등)
        UUID firstMemberId = targets.get(0).getMemberId();
        boolean alreadySeeded = overtimeRequestRepository
                .findAll().stream()
                .anyMatch(r -> r.getCompanyId().equals(companyId) && r.getMemberId().equals(firstMemberId));
        if (alreadySeeded) {
            log.info("[DEMO-SEED] OT 이미 시드됨 skip companyId={}", companyId);
            return;
        }

        LocalDate today = LocalDate.now();
        YearMonth lastMonth = YearMonth.from(today).minusMonths(1);
        int totalCreated = 0;

        for (int i = 0; i < picked; i++) {
            MemberResDto m = targets.get(i);
            int perMonth = perMonthByIdx[i];
            YearMonth start = YearMonth.from(m.getJoinDate());

            for (YearMonth ym = start; !ym.isAfter(lastMonth); ym = ym.plusMonths(1)) {
                int created = 0;
                int day = 2; // 매월 평일에서 골라 분산
                while (created < perMonth && day <= ym.lengthOfMonth()) {
                    LocalDate target = ym.atDay(day);
                    DayOfWeek dow = target.getDayOfWeek();
                    if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                        OvertimeRequest req = OvertimeRequest.builder()
                                .memberId(m.getMemberId())
                                .companyId(companyId)
                                .targetDate(target)
                                .requestType(OvertimeRequestType.PRE)
                                .plannedStartTime(LocalTime.of(18, 0))
                                .plannedEndTime(LocalTime.of(20, 0))
                                .requestedMinutes(120)
                                .actualStartTime(LocalTime.of(18, 0))
                                .actualEndTime(LocalTime.of(20, 0))
                                .actualMinutes(120)
                                .reason("프로젝트 마감 대응 (시드)")
                                .approvalStatus(OvertimeApprovalStatus.APPROVED)
                                .approvedMinutes(120)
                                .submittedAt(target.minusDays(1).atTime(9, 0))
                                .decidedAt(target.minusDays(1).atTime(14, 0))
                                .decidedBy(SYSTEM_ACTOR)
                                .build();
                        overtimeRequestRepository.save(req);
                        created++;
                        totalCreated++;
                    }
                    // 평일 분산 - 약 2~3일 간격으로 점프
                    day += (perMonth >= 10) ? 2 : (perMonth >= 5) ? 4 : 10;
                }
            }
            log.info("[DEMO-SEED] OT 시드 완료 - {} 월 {}건", m.getName(), perMonth);
        }
        log.info("[DEMO-SEED] OvertimeRequest 시드 완료 companyId={} 총 {}건 (이전 전체 {})",
                companyId, totalCreated, existing);
    }

    /**
     * 직원당 분기 1건 LeaveRequest 추가 (최근 12개월만)
     * - 분기 가운데 달 15일 (평일 보정)
     * - 그날에 이미 LeaveRequest 있으면 skip (멱등)
     * - 시드 시간 단축: 직원당 매월 → 분기, 최근 12개월만
     */
    private void seedAdditionalLeaveRequests(UUID companyId) {
        UUID annualTypeId = companyLeaveTypeRepository
                .findByCompanyIdAndCode(companyId, "ANNUAL")
                .map(CompanyLeaveType::getCompanyLeaveTypeId)
                .orElse(null);
        if (annualTypeId == null) {
            log.warn("[DEMO-SEED] 추가 LeaveRequest skip - ANNUAL 타입 없음 companyId={}", companyId);
            return;
        }

        ApiResponse<List<MemberResDto>> apiRes;
        try {
            apiRes = memberFeignClient.getMembersByCompany(companyId);
        } catch (Exception e) {
            log.warn("[DEMO-SEED] 추가 LeaveRequest - 직원 조회 실패 - {}", e.getMessage());
            return;
        }
        List<MemberResDto> members = apiRes != null ? apiRes.getData() : null;
        if (members == null || members.isEmpty()) return;

        LocalDate today = LocalDate.now();
        YearMonth lastMonth = YearMonth.from(today).minusMonths(1);
        // 최근 12개월만 시드
        YearMonth windowStart = YearMonth.from(today).minusMonths(12);
        int totalCreated = 0;

        for (MemberResDto m : members) {
            if (m.getJoinDate() == null) continue;
            YearMonth joinYm = YearMonth.from(m.getJoinDate());
            YearMonth start = joinYm.isAfter(windowStart) ? joinYm : windowStart;
            // 분기당 1건만 - 2,5,8,11월(분기 중간 달)에 시드
            for (YearMonth ym = start; !ym.isAfter(lastMonth); ym = ym.plusMonths(1)) {
                int mon = ym.getMonthValue();
                if (mon != 2 && mon != 5 && mon != 8 && mon != 11) continue;
                LocalDate target = ym.atDay(Math.min(15, ym.lengthOfMonth()));
                // 평일 보정 (토/일이면 직전 금요일로)
                while (target.getDayOfWeek() == DayOfWeek.SATURDAY
                        || target.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    target = target.minusDays(1);
                }
                // 멱등: 그날 LeaveRequest 있으면 skip
                boolean exists = !leaveRequestRepository
                        .findAllByMemberIdAndStartDateAndDelYn(m.getMemberId(), target, "N")
                        .isEmpty();
                if (exists) continue;

                LeaveRequest lr = LeaveRequest.builder()
                        .memberId(m.getMemberId())
                        .companyId(companyId)
                        .companyLeaveTypeId(annualTypeId)
                        .startDate(target)
                        .endDate(target)
                        .usageDays(1.0)
                        .deductedBalanceType(BalanceType.ANNUAL)
                        .reason("월별 연차 사용 (시드)")
                        .approvalStatus(LeaveApprovalStatus.APPROVED)
                        .requestedBy(m.getMemberId())
                        .requestedAt(target.minusDays(7).atTime(10, 0))
                        .decidedBy(SYSTEM_ACTOR)
                        .decidedAt(target.minusDays(5).atTime(15, 0))
                        .initiator(LeaveInitiator.SELF)
                        .build();
                leaveRequestRepository.save(lr);
                totalCreated++;
            }
        }
        log.info("[DEMO-SEED] 추가 LeaveRequest 시드 완료 companyId={} 신규 {}건", companyId, totalCreated);
    }

    /**
     * 연차사용촉진 시나리오 시드 (촉진제도 ON 회사만)
     * - 시드용 MemberBalance(expirationDate=2025-12-31, ANNUAL, 미사용 5일) 생성
     * - 회사 직원 4명 대상:
     *   * 2명: 1차 알림 → 회신(ACKNOWLEDGED) → 본인이 사용 (LeaveRequest)
     *   * 2명: 1차 알림 무응답 → 2차 알림 → 강제 지정(DESIGNATED) + LeaveRequest createDesignated
     * - 멱등: 회사에 PromotionLog 이미 있으면 skip
     */
    private void seedLeavePromotionLogs(UUID companyId) {
        UUID annualTypeId = companyLeaveTypeRepository
                .findByCompanyIdAndCode(companyId, "ANNUAL")
                .map(CompanyLeaveType::getCompanyLeaveTypeId)
                .orElse(null);
        if (annualTypeId == null) {
            log.warn("[DEMO-SEED] PromotionLog skip - ANNUAL 타입 없음");
            return;
        }

        ApiResponse<List<MemberResDto>> apiRes;
        try {
            apiRes = memberFeignClient.getMembersByCompany(companyId);
        } catch (Exception e) {
            log.warn("[DEMO-SEED] PromotionLog - 직원 조회 실패 - {}", e.getMessage());
            return;
        }
        List<MemberResDto> members = apiRes != null ? apiRes.getData() : null;
        if (members == null || members.isEmpty()) return;

        // 팀장/인사관리자 제외 + 입사일 순 정렬
        List<MemberResDto> targets = members.stream()
                .filter(m -> !"팀장".equals(m.getJobTitleName()))
                .filter(m -> !("인사팀".equals(m.getOrganizationName())
                        && "파트장".equals(m.getJobTitleName())))
                .filter(m -> m.getJoinDate() != null)
                // 2025-01-01 이전 입사자만 (만료 시점에 부여 자격)
                .filter(m -> m.getJoinDate().isBefore(LocalDate.of(2025, 1, 1)))
                .sorted((a, b) -> a.getJoinDate().compareTo(b.getJoinDate()))
                .limit(4)
                .toList();
        if (targets.isEmpty()) {
            log.info("[DEMO-SEED] PromotionLog 대상 없음 companyId={}", companyId);
            return;
        }

        // 멱등 체크 (회사에 이미 PromotionLog 있으면 skip)
        boolean alreadySeeded = !leavePromotionLogRepository
                .findByCompanyIdAndStageAndStatus(companyId, PromotionStage.FIRST, PromotionLogStatus.SENT)
                .isEmpty()
                || !leavePromotionLogRepository
                .findByCompanyIdAndStageAndStatus(companyId, PromotionStage.FIRST, PromotionLogStatus.ACKNOWLEDGED)
                .isEmpty()
                || !leavePromotionLogRepository
                .findByCompanyIdAndStageAndStatus(companyId, PromotionStage.SECOND, PromotionLogStatus.DESIGNATED)
                .isEmpty();
        if (alreadySeeded) {
            log.info("[DEMO-SEED] PromotionLog 이미 시드됨 skip companyId={}", companyId);
            return;
        }

        LocalDate firstSentOn = LocalDate.of(2025, 7, 4);  // 만료 180일 전
        LocalDate secondSentOn = LocalDate.of(2025, 11, 1); // 만료 60일 전
        LocalDate expiry = LocalDate.of(2025, 12, 31);

        for (int i = 0; i < targets.size(); i++) {
            MemberResDto m = targets.get(i);
            // 1) 시드용 MemberBalance 생성 (15일 부여, 10일 사용, 5일 미사용)
            MemberBalance balance = MemberBalance.builder()
                    .memberId(m.getMemberId())
                    .companyId(companyId)
                    .balanceType(BalanceType.ANNUAL)
                    .totalGranted(15.0)
                    .totalUsed(10.0)
                    .remaining(5.0)
                    .expirationDate(expiry)
                    .isUsableYn("N")  // 만료됨
                    .isExpireYn("Y")
                    .build();
            MemberBalance savedBalance = memberBalanceRepository.save(balance);

            // 2) 1차 알림 (모두 발송)
            LeavePromotionLog firstLog = LeavePromotionLog.builder()
                    .memberBalanceId(savedBalance.getMemberBalanceId())
                    .memberId(m.getMemberId())
                    .companyId(companyId)
                    .stage(PromotionStage.FIRST)
                    .sentOn(firstSentOn)
                    .status(PromotionLogStatus.SENT)
                    .build();

            if (i < 2) {
                // 시나리오 A: 회신 + 본인 사용
                firstLog.markViewed();
                firstLog.acknowledge("[\"2025-12-22\",\"2025-12-23\",\"2025-12-26\",\"2025-12-29\",\"2025-12-30\"]");
                leavePromotionLogRepository.save(firstLog);

                // 회신한 5일 분의 LeaveRequest 5건 (각 1일짜리)
                LocalDate[] usedDays = {
                        LocalDate.of(2025, 12, 22),
                        LocalDate.of(2025, 12, 23),
                        LocalDate.of(2025, 12, 26),
                        LocalDate.of(2025, 12, 29),
                        LocalDate.of(2025, 12, 30)
                };
                for (LocalDate d : usedDays) {
                    boolean exists = !leaveRequestRepository
                            .findAllByMemberIdAndStartDateAndDelYn(m.getMemberId(), d, "N")
                            .isEmpty();
                    if (exists) continue;
                    LeaveRequest lr = LeaveRequest.builder()
                            .memberId(m.getMemberId())
                            .companyId(companyId)
                            .companyLeaveTypeId(annualTypeId)
                            .startDate(d).endDate(d)
                            .usageDays(1.0)
                            .deductedBalanceType(BalanceType.ANNUAL)
                            .reason("촉진 1차 회신 후 자율 사용 (시드)")
                            .approvalStatus(LeaveApprovalStatus.APPROVED)
                            .requestedBy(m.getMemberId())
                            .requestedAt(firstSentOn.atTime(11, 0))
                            .decidedBy(SYSTEM_ACTOR)
                            .decidedAt(firstSentOn.plusDays(2).atTime(15, 0))
                            .initiator(LeaveInitiator.SELF)
                            .build();
                    leaveRequestRepository.save(lr);
                }
            } else {
                // 시나리오 B: 1차 무응답 → 2차 알림 → 강제 지정
                leavePromotionLogRepository.save(firstLog);

                LeavePromotionLog secondLog = LeavePromotionLog.builder()
                        .memberBalanceId(savedBalance.getMemberBalanceId())
                        .memberId(m.getMemberId())
                        .companyId(companyId)
                        .stage(PromotionStage.SECOND)
                        .sentOn(secondSentOn)
                        .status(PromotionLogStatus.SENT)
                        .build();
                String designated = "[\"2025-12-22\",\"2025-12-23\",\"2025-12-26\",\"2025-12-29\",\"2025-12-30\"]";
                secondLog.designate(designated, "1차 회신 무응답 - 회사 강제 지정");
                leavePromotionLogRepository.save(secondLog);

                // 강제 지정 LeaveRequest 5건 (ADMIN_DESIGNATION)
                LocalDate[] forcedDays = {
                        LocalDate.of(2025, 12, 22),
                        LocalDate.of(2025, 12, 23),
                        LocalDate.of(2025, 12, 26),
                        LocalDate.of(2025, 12, 29),
                        LocalDate.of(2025, 12, 30)
                };
                for (LocalDate d : forcedDays) {
                    boolean exists = !leaveRequestRepository
                            .findAllByMemberIdAndStartDateAndDelYn(m.getMemberId(), d, "N")
                            .isEmpty();
                    if (exists) continue;
                    LeaveRequest lr = LeaveRequest.createDesignated(
                            m.getMemberId(), companyId, annualTypeId,
                            d, d, 1.0, "촉진 2차 무응답 - 회사 강제 지정 (시드)",
                            SYSTEM_ACTOR);
                    leaveRequestRepository.save(lr);
                }
            }
            log.info("[DEMO-SEED] PromotionLog 시드 - {} 시나리오 {}", m.getName(), (i < 2 ? "A:회신" : "B:강제"));
        }
        log.info("[DEMO-SEED] LeavePromotionLog 시드 완료 companyId={} 대상 {}명", companyId, targets.size());
    }

    /**
     * 조퇴 + 근태정정 이력 시드 (결재 우회, 도메인 직접 update)
     * - 조퇴: 5명에게 직원당 1~2건 - DailyAttendance.markEarlyLeaveExcused()
     * - 근태정정: 모든 직원에게 2건씩 - AttendanceLog.markCorrected()
     * 멱등: 이미 표시된 일자는 skip
     */
    private void seedEarlyLeaveAndCorrections(UUID companyId) {
        ApiResponse<List<MemberResDto>> apiRes;
        try {
            apiRes = memberFeignClient.getMembersByCompany(companyId);
        } catch (Exception e) {
            log.warn("[DEMO-SEED] 조퇴/정정 - 직원 조회 실패 - {}", e.getMessage());
            return;
        }
        List<MemberResDto> members = apiRes != null ? apiRes.getData() : null;
        if (members == null || members.isEmpty()) return;

        List<MemberResDto> sorted = members.stream()
                .filter(m -> m.getJoinDate() != null)
                .sorted((a, b) -> a.getJoinDate().compareTo(b.getJoinDate()))
                .toList();

        LocalDate today = LocalDate.now();
        int earlyLeaveCount = 0;
        int correctionCount = 0;

        // 조퇴 - 직원 5명, 직원당 2건 (입사 후 6개월/12개월 시점 평일)
        for (int i = 0; i < Math.min(5, sorted.size()); i++) {
            MemberResDto m = sorted.get(i);
            int[] monthsAfter = { 6, 12 };
            for (int monthsAdd : monthsAfter) {
                LocalDate target = m.getJoinDate().plusMonths(monthsAdd).withDayOfMonth(8);
                if (!target.isBefore(today)) continue;
                while (target.getDayOfWeek() == DayOfWeek.SATURDAY
                        || target.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    target = target.plusDays(1);
                }
                var daOpt = dailyAttendanceRepository
                        .findByCompanyIdAndMemberIdAndAttendanceDate(companyId, m.getMemberId(), target);
                if (daOpt.isEmpty()) continue;
                DailyAttendance da = daOpt.get();
                if (da.isEarlyLeaveExcused()) continue;
                da.markEarlyLeaveExcused();
                dailyAttendanceRepository.save(da);
                earlyLeaveCount++;
            }
        }

        // 근태정정 - 모든 직원에게 2건씩 (입사 후 3/9개월 시점, CLOCK_IN/OUT 한건씩 정정 표시)
        for (MemberResDto m : sorted) {
            int[] monthsAfter = { 3, 9 };
            EventType[] eventTypes = { EventType.CLOCK_IN, EventType.CLOCK_OUT };
            for (int idx = 0; idx < monthsAfter.length; idx++) {
                LocalDate target = m.getJoinDate().plusMonths(monthsAfter[idx]).withDayOfMonth(11);
                if (!target.isBefore(today)) continue;
                while (target.getDayOfWeek() == DayOfWeek.SATURDAY
                        || target.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    target = target.plusDays(1);
                }
                var daOpt = dailyAttendanceRepository
                        .findByCompanyIdAndMemberIdAndAttendanceDate(companyId, m.getMemberId(), target);
                if (daOpt.isEmpty()) continue;
                DailyAttendance da = daOpt.get();
                EventType evt = eventTypes[idx];
                var logOpt = attendanceLogRepository
                        .findTop1ByDailyAttendanceAndEventTypeOrderByEventTimeDesc(da, evt);
                if (logOpt.isEmpty()) continue;
                AttendanceLog logEntry = logOpt.get();
                if ("Y".equals(logEntry.getIsCorrectedYn())) continue;
                String reason = evt == EventType.CLOCK_IN
                        ? "출근 시각 정정 (시드)"
                        : "퇴근 시각 정정 (시드)";
                logEntry.markCorrected(SYSTEM_ACTOR, reason);
                attendanceLogRepository.save(logEntry);
                correctionCount++;
            }
        }

        log.info("[DEMO-SEED] 조퇴 {}건 + 근태정정 {}건 시드 완료 companyId={}",
                earlyLeaveCount, correctionCount, companyId);
    }

    /**
     * 수당변경 이력 시드 (결재 우회 - APPROVED 직접 insert)
     * - 5명에게 식대 추가 (effectiveFrom = 6개월 전, 진행중)
     * - 3명에게 자가운전보조금 추가 후 3개월 후 해제 (effectiveTo 마감)
     * 멱등: 회사에 동일 템플릿 결재 이력(APPROVED 비-AUTO) 이미 있으면 skip
     */
    private void seedAllowanceChanges(UUID companyId) {
        UUID mealTplId = findTemplateId(companyId, "식대");
        UUID vehicleTplId = findTemplateId(companyId, "자가운전보조금");

        ApiResponse<List<MemberResDto>> apiRes;
        try {
            apiRes = memberFeignClient.getMembersByCompany(companyId);
        } catch (Exception e) {
            log.warn("[DEMO-SEED] 수당변경 - 직원 조회 실패 - {}", e.getMessage());
            return;
        }
        List<MemberResDto> members = apiRes != null ? apiRes.getData() : null;
        if (members == null || members.isEmpty()) return;

        List<MemberResDto> sorted = members.stream()
                .filter(m -> m.getJoinDate() != null)
                .sorted((a, b) -> a.getJoinDate().compareTo(b.getJoinDate()))
                .toList();

        LocalDate today = LocalDate.now();
        int created = 0;

        // 5명에게 식대 추가 (APPROVED, 6개월 전 발효, 진행중)
        if (mealTplId != null) {
            LocalDate effFrom = today.minusMonths(6).withDayOfMonth(1);
            for (int i = 0; i < Math.min(5, sorted.size()); i++) {
                MemberResDto m = sorted.get(i);
                boolean dup = memberAllowanceRepository.findAll().stream()
                        .anyMatch(a -> a.getMemberId().equals(m.getMemberId())
                                && mealTplId.equals(a.getSalaryItemTemplateId())
                                && a.getApprovalStatus() == AllowanceApprovalStatus.APPROVED
                                && effFrom.equals(a.getEffectiveFrom()));
                if (dup) continue;
                MemberAllowance ma = MemberAllowance.builder()
                        .memberId(m.getMemberId())
                        .companyId(companyId)
                        .salaryItemTemplateId(mealTplId)
                        .amount(200_000L)
                        .effectiveFrom(effFrom)
                        .effectiveTo(null)
                        .approvalStatus(AllowanceApprovalStatus.APPROVED)
                        .reason("식대 추가 결재 (시드)")
                        .requestedBy(m.getMemberId())
                        .requestedAt(effFrom.minusDays(7).atTime(10, 0))
                        .decidedBy(SYSTEM_ACTOR)
                        .decidedAt(effFrom.minusDays(3).atTime(15, 0))
                        .build();
                memberAllowanceRepository.save(ma);
                created++;
            }
        }

        // 3명에게 자가운전보조금 추가 후 3개월 후 해제
        if (vehicleTplId != null) {
            LocalDate effFrom = today.minusMonths(9).withDayOfMonth(1);
            LocalDate effTo = effFrom.plusMonths(3).withDayOfMonth(1).minusDays(1);
            for (int i = 0; i < Math.min(3, sorted.size()); i++) {
                MemberResDto m = sorted.get(i);
                boolean dup = memberAllowanceRepository.findAll().stream()
                        .anyMatch(a -> a.getMemberId().equals(m.getMemberId())
                                && vehicleTplId.equals(a.getSalaryItemTemplateId())
                                && effFrom.equals(a.getEffectiveFrom()));
                if (dup) continue;
                MemberAllowance ma = MemberAllowance.builder()
                        .memberId(m.getMemberId())
                        .companyId(companyId)
                        .salaryItemTemplateId(vehicleTplId)
                        .amount(200_000L)
                        .effectiveFrom(effFrom)
                        .effectiveTo(effTo) // 종료
                        .approvalStatus(AllowanceApprovalStatus.APPROVED)
                        .reason("자가운전보조금 추가 후 해제 결재 (시드)")
                        .requestedBy(m.getMemberId())
                        .requestedAt(effFrom.minusDays(7).atTime(10, 0))
                        .decidedBy(SYSTEM_ACTOR)
                        .decidedAt(effFrom.minusDays(3).atTime(15, 0))
                        .build();
                memberAllowanceRepository.save(ma);
                created++;
            }
        }

        log.info("[DEMO-SEED] 수당변경 시드 완료 companyId={} 신규 {}건", companyId, created);
    }

    /**
     * Historical 연차 부여 - 직원 입사년도부터 올해까지 매년 ANNUAL balance 시드
     * - 회계연도 회사: 매년 1/1 LeaveGrantWorker 호출
     * - 입사일 회사: 직원별 입사 기념일에 LeaveGrantWorker 호출
     * LeaveGrantWorker 자체 멱등 (이미 부여된 직원 skip)
     */
    private void seedHistoricalLeaveGrants(UUID companyId) {
        ApiResponse<List<MemberResDto>> apiRes;
        try {
            apiRes = memberFeignClient.getMembersByCompany(companyId);
        } catch (Exception e) {
            log.warn("[DEMO-SEED] historical 부여 - 직원 조회 실패 - {}", e.getMessage());
            return;
        }
        List<MemberResDto> members = apiRes != null ? apiRes.getData() : null;
        if (members == null || members.isEmpty()) return;

        LocalDate today = LocalDate.now();
        // 현재 연도 1/1만 호출 - LeaveGrantWorker 가 근속 연수 기반으로 정확한 일수 부여
        // (12년차 → 19일, 5년차 → 17일 등). 매년 누적 부여하면 만료 처리 안 돼 잔고 누적 문제 발생.
        LocalDate base = LocalDate.of(today.getYear(), 1, 1);
        if (!base.isAfter(today)) {
            try {
                leaveGrantWorker.runForCompany(companyId, base);
            } catch (Exception e) {
                log.warn("[DEMO-SEED] LeaveGrantWorker(1/1) 실패 base={} - {}", base, e.getMessage());
            }
        }
        log.info("[DEMO-SEED] 연차 부여 완료 companyId={} (현재 연도만)", companyId);
    }

    /**
     * 직원별 정상 근무 가정 Ledger 1건 생성
     * - 매 3개월마다 OT 5시간 추가 (12개월 환산 다양성 확보)
     */
    private void seedLedger(UUID memberId, UUID companyId, YearMonth ym) {
        int regular = 209 * 60;                                           // 12540분 표준
        int overtime = (ym.getMonthValue() % 3 == 0) ? 5 * 60 : 0;        // 매 3개월마다 5h OT
        int total = regular + overtime;

        MonthlyAttendanceLedger ledger = MonthlyAttendanceLedger.builder()
                .memberId(memberId)
                .companyId(companyId)
                .ledgerYearMonth(ym.toString())
                .totalWorkedMinutes(total)
                .regularMinutes(regular)
                .overtimeMinutes(overtime)
                .nightMinutes(0)
                .holidayMinutes(0)
                .leaveMinutes(0)
                .lateMinutes(0)
                .earlyLeaveMinutes(0)
                .absentDays(0)
                .closedAt(ym.atEndOfMonth().atTime(2, 0))
                .closedBy(SYSTEM_ACTOR)
                .isLocked(false)
                .build();
        ledgerRepository.save(ledger);
    }

    /**
     * 회사 직원 list -> 직원별 Salary + 일부 직원에 MemberAllowance 시드
     *
     * Salary 결정:
     *  - 입사일 = effectiveFrom
     *  - 호봉제: step 1~6 (입사 순서대로) - baseSalary 는 PayGradeTable 에서 자동 (시드는 step 만)
     *    실제 PayrollService 가 baseSalary 를 사용할 때 호봉표 lookup 안 함 - Salary.baseSalary 직접 보므로
     *    호봉제도 baseSalary 명시 필요 (PayGradeTable 의 250만 + (step-1) * 50만 패턴 일치)
     *  - 연봉제: 입사 기간별 차등 (장기근속 ↑)
     *
     * 부양가족수 / 자녀수 다양화 - 소득세 간이세액 룩업 검증용
     *
     * MemberAllowance:
     *  - i=0 팀장 (24개월) -> 직책수당 30만 (회사 4 / 회사 5)
     *  - i=3 팀장 (9개월)  -> 직책수당 30만
     *  - i=2 (12개월) -> 자녀수당 10만 × 2명
     */
    private void seedEmployees(UUID companyId, UUID salaryPolicyId, boolean usePayGrade) {
        ApiResponse<List<MemberResDto>> apiRes;
        try {
            apiRes = memberFeignClient.getMembersByCompany(companyId);
        } catch (Exception e) {
            log.warn("[DEMO-SEED] 직원 list 조회 실패 companyId={} - {}", companyId, e.getMessage());
            return;
        }
        List<MemberResDto> members = apiRes != null ? apiRes.getData() : null;
        if (members == null || members.isEmpty()) {
            log.warn("[DEMO-SEED] 직원 없음 companyId={}", companyId);
            return;
        }

        // SalaryItemTemplate 조회 - 직책 / 자녀 / 연구활동비 / 고정 금액 항목들
        UUID positionAllowanceTplId = findTemplateId(companyId, "직책수당");
        UUID childAllowanceTplId = findTemplateId(companyId, "자녀수당");
        UUID researchAllowanceTplId = findTemplateId(companyId, "연구활동비");
        // 고정 금액 항목 (이전엔 회사 공통 자동 적용이었으나 opt-in 모델로 직원별 명시 부여)
        UUID mealAllowanceTplId = findTemplateId(companyId, "식대");
        UUID vehicleAllowanceTplId = findTemplateId(companyId, "자가운전보조금");
        UUID childcareAllowanceTplId = findTemplateId(companyId, "보육수당");

        // 입사일 빠른 순으로 정렬 (장기근속 i=0)
        List<MemberResDto> sorted = members.stream()
                .filter(m -> m.getJoinDate() != null)
                .sorted((a, b) -> a.getJoinDate().compareTo(b.getJoinDate()))
                .toList();

        // 직원별 baseSalary / 부양가족 셋업 (인덱스 기반 다양화)
        long[] yearlySalaryByIdx = { 5_000_000L, 4_500_000L, 4_000_000L, 3_500_000L, 3_000_000L, 2_500_000L };
        int[] dependentByIdx = { 3, 2, 4, 1, 2, 1 };
        int[] childByIdx = { 1, 0, 2, 0, 1, 0 };

        // 입사일 기준 baseSalary - 그 후로 매년 1/1 마다 인상된 행 추가 시드
        for (int i = 0; i < sorted.size(); i++) {
            MemberResDto member = sorted.get(i);
            // 이미 시드된 Salary 있으면 skip (멱등)
            boolean alreadyExists = salaryRepository
                    .findActiveSalary(member.getMemberId(), companyId, LocalDate.now())
                    .isPresent();
            if (alreadyExists) {
                log.info("[DEMO-SEED] Salary 이미 있음 skip memberId={}", member.getMemberId());
                continue;
            }

            int idx = Math.min(i, yearlySalaryByIdx.length - 1);
            long startBaseSalary = yearlySalaryByIdx[idx];

            LocalDate joinDate = member.getJoinDate();
            LocalDate today = LocalDate.now();
            int currentYear = today.getYear();

            // 호봉제: 근속 연수 기반 시작 호봉 분배 (1~50호봉 폭넓게)
            // PayGradeTable 기준 (250만 + 100,000 * (step-1))
            Integer startStep = null;
            if (usePayGrade) {
                int tenureYears = (int) ChronoUnit.YEARS.between(joinDate, today);
                if (tenureYears >= 12) startStep = 35;
                else if (tenureYears >= 10) startStep = 28;
                else if (tenureYears >= 8) startStep = 22;
                else if (tenureYears >= 5) startStep = 15;
                else if (tenureYears >= 3) startStep = 9;
                else if (tenureYears >= 1) startStep = 4;
                else startStep = 1;
                // 입사 시점부터 매년 인상이므로 시작 호봉 = startStep - tenureYears (음수 방지)
                int adjustedStart = Math.max(1, startStep - tenureYears);
                startStep = adjustedStart;
                startBaseSalary = 2_500_000L + 100_000L * (startStep - 1);
            }

            long curBase = startBaseSalary;
            Integer curStep = startStep;
            Salary prevSalary = null;

            // 입사년도 ~ 올해까지 매년 행 생성 (첫 해는 입사일, 이후는 1/1)
            for (int year = joinDate.getYear(); year <= currentYear; year++) {
                LocalDate periodStart = (year == joinDate.getYear())
                        ? joinDate.withDayOfMonth(1)
                        : LocalDate.of(year, 1, 1);

                // 입사 다음 해부터 매년 호봉/baseSalary 인상 (호봉제: step+1 cap=50 / 비호봉제: +5% 만원 단위)
                if (year > joinDate.getYear()) {
                    if (usePayGrade && curStep != null) {
                        curStep = Math.min(curStep + 1, 50);
                        curBase = 2_500_000L + 100_000L * (curStep - 1);
                    } else {
                        long raised = (long) (curBase * 1.05);
                        curBase = (raised / 10000) * 10000;
                    }
                }

                // 이전 행 닫기 (effectiveTo = 새 행 시작일 -1)
                if (prevSalary != null) {
                    prevSalary.closeEffectivePeriod(periodStart.minusDays(1));
                    salaryRepository.save(prevSalary);
                }

                Salary salary = Salary.builder()
                        .memberId(member.getMemberId())
                        .companyId(companyId)
                        .salaryPolicyId(salaryPolicyId)
                        .step(curStep)
                        .baseSalary(curBase)
                        .jobGradeName(member.getJobGradeName())
                        .jobTitleName(member.getJobTitleName())
                        .effectiveFrom(periodStart)
                        .dependentCount(dependentByIdx[idx])
                        .childUnder20Count(childByIdx[idx])
                        .taxReductionType(TaxReductionType.NONE)
                        .taxReductionRate(BigDecimal.ZERO)
                        .build();
                prevSalary = salaryRepository.save(salary);
            }

            // 직책수당 - 부장(i=0) / 팀장(i=3, i=13) / 차장 (i=6, i=9)
            if (positionAllowanceTplId != null) {
                Long posAmount = null;
                String posReason = null;
                if (i == 0) { posAmount = 500_000L; posReason = "데모 시드 - 부장 직책수당"; }
                else if (i == 3 || i == 13) { posAmount = 300_000L; posReason = "데모 시드 - 팀장 직책수당"; }
                else if (i == 6 || i == 9) { posAmount = 200_000L; posReason = "데모 시드 - 차장 직책수당"; }
                if (posAmount != null) {
                    saveAllowance(companyId, member.getMemberId(), positionAllowanceTplId,
                            posAmount, member.getJoinDate(), posReason);
                }
            }
            // 자녀수당 - 자녀 보유 직원 (idx 패턴 따라 회사당 6~8명)
            if (childAllowanceTplId != null && i >= 2 && i % 3 == 2) {
                int kids = (i % 2 == 0) ? 2 : 1;
                long childAmt = kids * 100_000L;
                saveAllowance(companyId, member.getMemberId(), childAllowanceTplId,
                        childAmt, member.getJoinDate(),
                        String.format("데모 시드 - 자녀 %d명 수당", kids));
            }
            // 연구활동비 - 개발팀 일부 직원 (i=7~12)
            if (researchAllowanceTplId != null && i >= 7 && i <= 12) {
                saveAllowance(companyId, member.getMemberId(), researchAllowanceTplId,
                        200_000L, member.getJoinDate(), "데모 시드 - 연구활동비");
            }
            // 고정 금액 항목 일괄 부여 - 식대/자가운전/보육수당 (모든 직원에게 명시 부여)
            // opt-in 모델 - 자동 적용 폐기됐으니 명시 부여 필수
            if (mealAllowanceTplId != null) {
                saveAllowance(companyId, member.getMemberId(), mealAllowanceTplId,
                        200_000L, member.getJoinDate(), "데모 시드 - 식대 (고정)");
            }
            if (vehicleAllowanceTplId != null) {
                saveAllowance(companyId, member.getMemberId(), vehicleAllowanceTplId,
                        200_000L, member.getJoinDate(), "데모 시드 - 자가운전보조금 (고정)");
            }
            // 보육수당은 자녀 있는 직원만 (자녀수당 부여 패턴과 동일)
            if (childcareAllowanceTplId != null && i >= 2 && i % 3 == 2) {
                saveAllowance(companyId, member.getMemberId(), childcareAllowanceTplId,
                        200_000L, member.getJoinDate(), "데모 시드 - 보육수당 (고정)");
            }
        }
        log.info("[DEMO-SEED] 직원 Salary + Allowance 시드 완료 companyId={} 직원={}",
                companyId, sorted.size());
    }

    private UUID findTemplateId(UUID companyId, String itemName) {
        return salaryItemTemplateRepository
                .findByCompanyIdAndItemNameAndDelYn(companyId, itemName, "N")
                .map(SalaryItemTemplate::getSalaryItemTemplateId)
                .orElse(null);
    }

    private void saveAllowance(UUID companyId, UUID memberId, UUID templateId,
                               long amount, LocalDate effectiveFrom, String reason) {
        MemberAllowance allowance = MemberAllowance.builder()
                .memberId(memberId)
                .companyId(companyId)
                .salaryItemTemplateId(templateId)
                .amount(amount)
                .effectiveFrom(effectiveFrom)
                .approvalStatus(AllowanceApprovalStatus.AUTO) // 시스템 자동 세팅 (결재 생략)
                .reason(reason)
                .requestedBy(SYSTEM_ACTOR)
                .requestedAt(LocalDateTime.now())
                .decidedBy(SYSTEM_ACTOR)
                .decidedAt(LocalDateTime.now())
                .build();
        memberAllowanceRepository.save(allowance);
    }

    /**
     * 직원별 ANNUAL 연차 잔여 시드 - 입사 기간별 차등 부여 + 일부 사용 처리
     *
     * 한국 근로기준법:
     *  - 입사 1년 미만: 매월 만근당 1일 (최대 11일)
     *  - 입사 1년 이상: 15일 (3년차마다 +1, 최대 25일)
     *
     * 시드 패턴 (단순화):
     *  - 24개월 이상 (장기근속): 16일 부여 / 7일 사용 / 잔여 9일
     *  - 12-24개월 (1년 이상):   15일 부여 / 5일 사용 / 잔여 10일
     *  - 6-12개월 (1년 미만):     8일 부여 / 2일 사용 / 잔여 6일
     *  - 3-6개월:                4일 부여 / 1일 사용 / 잔여 3일
     *  - 0-3개월 (신규):          2일 부여 / 0일 사용 / 잔여 2일
     *
     * 멱등 - 직원의 활성 ANNUAL 잔액 1건이라도 있으면 skip
     */
    private void seedMemberBalances(UUID companyId) {
        ApiResponse<List<MemberResDto>> apiRes;
        try {
            apiRes = memberFeignClient.getMembersByCompany(companyId);
        } catch (Exception e) {
            log.warn("[DEMO-SEED] 직원 list 조회 실패 (MemberBalance) - {}", e.getMessage());
            return;
        }
        List<MemberResDto> members = apiRes != null ? apiRes.getData() : List.of();
        if (members.isEmpty()) return;

        LocalDate today = LocalDate.now();
        int created = 0;
        int skipped = 0;
        for (MemberResDto m : members) {
            if (m.getJoinDate() == null) continue;
            // 멱등 체크 - 활성 ANNUAL 잔액 있으면 skip
            if (memberBalanceRepository
                    .findAnnualBalance(companyId, m.getMemberId()).isPresent()) {
                skipped++;
                continue;
            }
            long monthsServed = ChronoUnit.MONTHS.between(m.getJoinDate(), today);
            double granted;
            double used;
            if (monthsServed >= 24) {
                granted = 16.0; used = 7.0;
            } else if (monthsServed >= 12) {
                granted = 15.0; used = 5.0;
            } else if (monthsServed >= 6) {
                granted = 8.0; used = 2.0;
            } else if (monthsServed >= 3) {
                granted = 4.0; used = 1.0;
            } else {
                granted = 2.0; used = 0.0;
            }
            // 만료일 - 회계연도 1년 (단순화) - 입사일+1년 또는 today+1년 중 큰 값
            LocalDate expiration = today.plusYears(1);
            MemberBalance balance = MemberBalance.builder()
                    .memberId(m.getMemberId())
                    .companyId(companyId)
                    .balanceType(BalanceType.ANNUAL)
                    .totalGranted(granted)
                    .totalUsed(used)
                    .remaining(granted - used)
                    .expirationDate(expiration)
                    .isUsableYn("Y")
                    .isExpireYn("N")
                    .carryoverConsentYn("N")
                    .delYn("N")
                    .build();
            memberBalanceRepository.save(balance);
            created++;
        }
        log.info("[DEMO-SEED] MemberBalance(ANNUAL) 시드 - 생성={} skip={} companyId={}",
                created, skipped, companyId);
    }

    /**
     * 직원별 일별 출퇴근 시드 - 입사일 ~ 어제까지 평일 (월~금)
     *
     * 직원 인덱스별 근무 패턴:
     *  i=0 (장기근속/팀장): 09:00 출근 / 18:00 퇴근 (표준 8시간)
     *  i=1 : 09:00 / 18:00 + 화/목 19:00 (주 2일 OT 1시간 = 주 2시간)
     *  i=2 : 09:00 / 19:30 (매일 +1.5시간 OT = 주 7.5시간)
     *  i=3 (팀장 - 주 52시간 풀): 09:00 / 20:30 매일 (일 11.5시간 = 주 12시간 OT, 점심 1시간 제외 후 일 10.5시간 근무 + 1.5h OT - 주 7.5h 정도)
     *       정확히 주 52시간 (40 + 12) 채우려면 매일 09:00 출근 / 20:30 퇴근 (휴게 1시간 차감 = 일 10.5시간, 주 52.5)
     *  i=4 : 09:00 / 18:00 표준
     *  i=5 (신규): 09:00 / 18:30 (일 0.5h OT = 주 2.5h)
     *
     * 휴게시간 1시간 (12-13시 점심) 가정
     * 주말은 출퇴근 기록 없음
     *
     * 멱등: 같은 (memberId, attendanceDate) 있으면 skip
     */
    /**
     * 회사 공휴일 자동 import - 법정 공휴일을 직전 3년 + 올해 + 내년까지 가져와 CompanyHoliday 에 채움.
     * daily 시드에서 holidaySet 으로 휴일 skip 하기 위해 daily 시드보다 먼저 호출.
     * 멱등: refreshLegalHolidays 가 해당 연도의 isLegalYn=Y 만 삭제 후 재삽입, 사용자 등록 휴일은 보존.
     */
    private void seedCompanyHolidays(UUID companyId) {
        log.info("[DEMO-SEED] >>> seedCompanyHolidays 시작 companyId={}", companyId);
        int currentYear = LocalDate.now().getYear();
        int total = 0;
        for (int year = currentYear - 3; year <= currentYear + 1; year++) {
            try {
                int imported = companyHolidayService.refreshLegalHolidays(companyId, year);
                total += imported;
                log.info("[DEMO-SEED]   - {}년 외부 API import 결과 = {}건", year, imported);
            } catch (Exception e) {
                log.warn("[DEMO-SEED]   - {}년 외부 API 실패 - {} (fallback 적용)", year, e.getMessage());
            }
        }
        // 외부 API 실패 또는 0건일 때 fallback - 핵심 한국 공휴일 하드코딩 (시연 안정성)
        int existingCount = companyHolidayRepository
                .findByCompanyIdAndDelYnOrderByHolidayDate(companyId, "N").size();
        log.info("[DEMO-SEED] 외부 API import 후 CompanyHoliday DB 수 = {}", existingCount);
        if (existingCount == 0) {
            log.warn("[DEMO-SEED] 외부 API 가 모두 실패 - 핵심 한국 공휴일 fallback 시드 적용");
            int fallback = seedFallbackKoreanHolidays(companyId, currentYear);
            log.info("[DEMO-SEED] fallback 공휴일 {}건 시드 완료", fallback);
            total += fallback;
        }
        log.info("[DEMO-SEED] <<< seedCompanyHolidays 완료 companyId={} 총={}건", companyId, total);
    }

    /** 외부 공공 API 가 실패할 때 fallback - 직전 3년 + 올해 + 내년의 핵심 한국 공휴일을 하드코딩 시드 */
    private int seedFallbackKoreanHolidays(UUID companyId, int currentYear) {
        // 양력 고정 공휴일만 (음력 설/추석은 매년 다르므로 외부 API 의존)
        // 양력 고정: 1/1 신정, 3/1 삼일절, 5/1 노동절, 5/5 어린이날, 6/6 현충일,
        //          8/15 광복절, 10/3 개천절, 10/9 한글날, 12/25 성탄절
        Object[][] fixed = new Object[][] {
                {1, 1, "신정"},
                {3, 1, "삼일절"},
                {5, 1, "노동절"},
                {5, 5, "어린이날"},
                {6, 6, "현충일"},
                {8, 15, "광복절"},
                {10, 3, "개천절"},
                {10, 9, "한글날"},
                {12, 25, "성탄절"},
        };
        int created = 0;
        for (int year = currentYear - 3; year <= currentYear + 1; year++) {
            for (Object[] f : fixed) {
                int month = (int) f[0];
                int day = (int) f[1];
                String name = (String) f[2];
                LocalDate date = LocalDate.of(year, month, day);
                // 멱등 - 같은 날짜·이름 이미 있으면 skip
                boolean exists = companyHolidayRepository
                        .findByCompanyIdAndDelYnOrderByHolidayDate(companyId, "N")
                        .stream()
                        .anyMatch(h -> date.equals(h.getHolidayDate()));
                if (exists) continue;
                companyHolidayRepository.save(
                        CompanyHoliday.builder()
                                .companyId(companyId)
                                .holidayDate(date)
                                .holidayName(name)
                                .isPaidYn("Y")
                                .isLegalYn("Y")
                                .build());
                created++;
            }
        }
        return created;
    }

    private void seedDailyAttendance(UUID companyId) {
        ApiResponse<List<MemberResDto>> apiRes;
        try {
            apiRes = memberFeignClient.getMembersByCompany(companyId);
        } catch (Exception e) {
            log.warn("[DEMO-SEED] 직원 list 조회 실패 (DailyAttendance) - {}", e.getMessage());
            return;
        }
        List<MemberResDto> members = apiRes != null ? apiRes.getData() : List.of();
        if (members.isEmpty()) return;

        // 입사일 빠른 순으로 정렬 - 인덱스로 패턴 분기 일관 유지
        List<MemberResDto> sorted = members.stream()
                .filter(m -> m.getJoinDate() != null)
                .sorted((a, b) -> a.getJoinDate().compareTo(b.getJoinDate()))
                .toList();

        LocalDate yesterday = LocalDate.now().minusDays(1);
        int totalCreated = 0;
        int totalSkipped = 0;

        // 공휴일 set - 시드에서 휴일에는 근무 데이터 안 만듦 (정상 휴무 시뮬)
        Set<LocalDate> holidaySet = companyHolidayRepository
                .findByCompanyIdAndDelYnOrderByHolidayDate(companyId, "N")
                .stream()
                .map(h -> h.getHolidayDate())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 휴가 종류 매핑 - LEAVE -> ANNUAL, HALF -> HALF_PM (없으면 LeaveRequest 생성 skip)
        UUID annualTypeId = companyLeaveTypeRepository
                .findByCompanyIdAndCode(companyId, "ANNUAL")
                .map(CompanyLeaveType::getCompanyLeaveTypeId)
                .orElse(null);
        UUID halfPmTypeId = companyLeaveTypeRepository
                .findByCompanyIdAndCode(companyId, "HALF_PM")
                .map(CompanyLeaveType::getCompanyLeaveTypeId)
                .orElse(null);
        UUID halfAmTypeId = companyLeaveTypeRepository
                .findByCompanyIdAndCode(companyId, "HALF_AM")
                .map(CompanyLeaveType::getCompanyLeaveTypeId)
                .orElse(null);
        log.info("[DEMO-SEED] >>> seedDailyAttendance 시작 companyId={} 직원={}명 holidaySet={}건 annualTypeId={} halfPmTypeId={} halfAmTypeId={}",
                companyId, sorted.size(), holidaySet.size(), annualTypeId, halfPmTypeId, halfAmTypeId);

        for (int i = 0; i < sorted.size(); i++) {
            MemberResDto member = sorted.get(i);
            // 6개 패턴 순환 - i=0..24 모두 다양한 출퇴근 시간 + 특수 근태(휴가/출장/지각/결근) 분포
            int idx = i % 6;
            // 직원별 출/퇴근 시간 분기 (24시간 기준)
            // {clockIn시, clockIn분, clockOut시, clockOut분, 화목추가OT여부}
            int clockInH, clockInM, clockOutH, clockOutM;
            boolean tueThuExtra = false;
            switch (idx) {
                case 0 -> { clockInH = 9; clockInM = 0; clockOutH = 18; clockOutM = 0; }
                case 1 -> { clockInH = 9; clockInM = 0; clockOutH = 18; clockOutM = 0; tueThuExtra = true; }
                case 2 -> { clockInH = 9; clockInM = 0; clockOutH = 19; clockOutM = 30; }
                case 3 -> { clockInH = 9; clockInM = 0; clockOutH = 20; clockOutM = 30; }  // 주 52시간 풀
                case 4 -> { clockInH = 9; clockInM = 0; clockOutH = 18; clockOutM = 0; }
                default -> { clockInH = 9; clockInM = 0; clockOutH = 18; clockOutM = 30; }
            }

            int memberCreated = 0;
            int memberSkipped = 0;
            // 근태 시드 시작일 cap - max(joinDate, today-12개월), 최근 12개월치 일별 근태
            LocalDate seedStart = member.getJoinDate().isBefore(LocalDate.now().minusMonths(12))
                    ? LocalDate.now().minusMonths(12)
                    : member.getJoinDate();
            // AttendanceLog 시드 cutoff - 최근 3개월만 (근태정정 시연용)
            LocalDate logCutoff = LocalDate.now().minusMonths(3);
            for (LocalDate d = seedStart; !d.isAfter(yesterday); d = d.plusDays(1)) {
                int dow = d.getDayOfWeek().getValue(); // 1=월 ~ 7=일
                if (dow == 6 || dow == 7) continue; // 토/일 skip
                if (holidaySet.contains(d)) continue; // 공휴일 skip - 정상 휴무 시뮬

                // 멱등 체크 - 기존 DailyAttendance 가 LEAVE/HALF 면 LeaveRequest 백필 (한 번만 실행)
                var existingDaily = dailyAttendanceRepository
                        .findByMemberIdAndAttendanceDate(member.getMemberId(), d);
                if (existingDaily.isPresent()) {
                    AttendanceStatus existingStatus = existingDaily.get().getStatus();
                    UUID typeId = existingStatus == AttendanceStatus.LEAVE ? annualTypeId
                            : existingStatus == AttendanceStatus.HALF ? halfPmTypeId : null;
                    if (typeId != null
                            && leaveRequestRepository
                            .findAllByMemberIdAndStartDateAndDelYn(member.getMemberId(), d, "N")
                            .isEmpty()) {
                        double usage = existingStatus == AttendanceStatus.LEAVE ? 1.0 : 0.5;
                        String reason = existingStatus == AttendanceStatus.LEAVE
                                ? "연차 사용 (시드 백필)"
                                : "오후 반차 사용 (시드 백필)";
                        leaveRequestRepository.save(LeaveRequest.builder()
                                .memberId(member.getMemberId())
                                .companyId(companyId)
                                .companyLeaveTypeId(typeId)
                                .startDate(d)
                                .endDate(d)
                                .usageDays(usage)
                                .deductedBalanceType(BalanceType.ANNUAL)
                                .reason(reason)
                                .approvalStatus(LeaveApprovalStatus.APPROVED)
                                .requestedBy(member.getMemberId())
                                .requestedAt(d.minusDays(7).atTime(10, 0))
                                .decidedBy(SYSTEM_ACTOR)
                                .decidedAt(d.minusDays(5).atTime(15, 0))
                                .initiator(LeaveInitiator.SELF)
                                .build());
                    }
                    memberSkipped++;
                    continue;
                }

                // 매월 특정일 - 직원 인덱스 따라 휴가/출장/외근/반차 패턴 분기
                // 시연용 다양화: 매월 한두 날에 직원 i 별로 다른 status 적용
                AttendanceStatus specialStatus = null;
                WorkTripType specialTripType = null;
                int dayOfMonth = d.getDayOfMonth();
                int monthVal = d.getMonthValue();

                // 직원 인덱스 + 월 모듈로 분기 - 월별 직원별 1회씩
                // 매월 (i+5)일에 해당 직원 특수 근태 (평일이면)
                int memberSpecialDay = 5 + idx; // i=0 -> 5일, i=1 -> 6일, ... i=5 -> 10일
                if (dayOfMonth == memberSpecialDay) {
                    // 짝수월 vs 홀수월로 다양화
                    if (monthVal % 4 == idx % 4) {
                        // 매 4개월마다 - 휴가/반차/출장/외근 순환
                        switch (idx % 4) {
                            case 0 -> specialStatus = AttendanceStatus.LEAVE;       // 연차
                            case 1 -> specialTripType = WorkTripType.BUSINESS_TRIP; // 출장
                            case 2 -> specialStatus = AttendanceStatus.HALF;        // 반차
                            case 3 -> specialTripType = WorkTripType.OUTSIDE_WORK;  // 외근
                        }
                    }
                }
                // 추가: 매 분기 마지막 달 20일 - 직원 idx=2,3 출장 (중간 직급은 분기 출장 자주)
                if ((monthVal == 3 || monthVal == 6 || monthVal == 9 || monthVal == 12)
                        && dayOfMonth == 20 && (idx == 2 || idx == 3)) {
                    specialTripType = WorkTripType.BUSINESS_TRIP;
                }
                // 지각 패턴 - idx=4 직원 매월 12일 09:30 출근 (특수 근태 없을 때만)
                boolean isTardy = idx == 4 && dayOfMonth == 12
                        && specialStatus == null && specialTripType == null;
                // 무단결근 패턴 - idx=5 직원 분기 마지막 달 28일 ABSENT (특수 근태 없을 때만)
                boolean isAbsent = idx == 5 && dayOfMonth == 28
                        && (monthVal == 3 || monthVal == 6 || monthVal == 9 || monthVal == 12)
                        && specialStatus == null && specialTripType == null;
                // 화/목 추가 OT 1시간 (idx=1)
                int outH = clockOutH;
                int outM = clockOutM;
                if (tueThuExtra && (dow == 2 || dow == 4)) {
                    outH = 19;
                    outM = 0;
                }

                LocalDateTime clockIn;
                LocalDateTime clockOut;
                int worked;
                int overtime;
                AttendanceStatus statusToSet = AttendanceStatus.NORMAL;

                if (isAbsent) {
                    // 무단결근 - 출퇴근 기록 없음, ABSENT
                    clockIn = null;
                    clockOut = null;
                    worked = 0;
                    overtime = 0;
                    statusToSet = AttendanceStatus.ABSENT;
                } else if (specialStatus == AttendanceStatus.LEAVE) {
                    // 종일 휴가 - 출퇴근 기록 없음
                    clockIn = null;
                    clockOut = null;
                    worked = 0;
                    overtime = 0;
                    statusToSet = AttendanceStatus.LEAVE;
                } else if (specialStatus == AttendanceStatus.HALF) {
                    // 반차 - 짝수월 = 오후 반차 (09~13), 홀수월 = 오전 반차 (14~18)
                    // 한국 표준: 오전 반차는 09~13 출근/퇴근, 오후 반차는 14~18 출근/퇴근
                    if (monthVal % 2 == 0) {
                        // 오후 반차 - 오전 09:00~13:00 근무 후 퇴근
                        clockIn = d.atTime(9, 0);
                        clockOut = d.atTime(13, 0);
                    } else {
                        // 오전 반차 - 오후 14:00~18:00 근무 (오전 휴식)
                        clockIn = d.atTime(14, 0);
                        clockOut = d.atTime(18, 0);
                    }
                    worked = 4 * 60;
                    overtime = 0;
                    statusToSet = AttendanceStatus.HALF;
                } else {
                    // 정상 또는 출장/외근 - 출퇴근 기록 있음 (출장 시 기본 스케줄 사용)
                    int actualInH = isTardy ? 9 : clockInH;
                    int actualInM = isTardy ? 30 : clockInM;
                    clockIn = d.atTime(actualInH, actualInM);
                    clockOut = d.atTime(outH, outM);
                    int totalMinutes = (int) java.time.Duration.between(clockIn, clockOut).toMinutes();
                    int breakMinutes = 60; // 점심 1시간
                    worked = Math.max(0, totalMinutes - breakMinutes);
                    int scheduleMinutes = 8 * 60; // 표준 8시간
                    overtime = Math.max(0, worked - scheduleMinutes);
                    statusToSet = AttendanceStatus.NORMAL;
                }

                DailyAttendance daily = DailyAttendance.builder()
                        .memberId(member.getMemberId())
                        .companyId(companyId)
                        .attendanceDate(d)
                        .status(statusToSet)
                        .closureStatus(ClosureStatus.FINALIZED)
                        .firstClockIn(clockIn)
                        .lastClockOut(clockOut)
                        .workedMinutes(worked)
                        .overtimeMinutes(overtime)
                        .earlyLeaveExcusedYn("N")
                        .build();
                DailyAttendance savedDaily = dailyAttendanceRepository.save(daily);

                // 출/퇴근 로그 - 최근 3개월만 시드 (시드 시간 단축, 정정 시연 보존)
                // 메인 근태 화면은 DailyAttendance.firstClockIn/lastClockOut 에서 표시되므로 로그 누락 OK
                if (clockIn != null && clockOut != null && !d.isBefore(logCutoff)) {
                    AttendanceLog logIn = AttendanceLog.builder()
                            .dailyAttendance(savedDaily)
                            .memberId(member.getMemberId())
                            .eventType(EventType.CLOCK_IN)
                            .eventTime(clockIn)
                            .sourceType(SourceType.WEB)
                            .isCorrectedYn("N")
                            .build();
                    attendanceLogRepository.save(logIn);

                    AttendanceLog logOut = AttendanceLog.builder()
                            .dailyAttendance(savedDaily)
                            .memberId(member.getMemberId())
                            .eventType(EventType.CLOCK_OUT)
                            .eventTime(clockOut)
                            .sourceType(SourceType.WEB)
                            .isCorrectedYn("N")
                            .build();
                    attendanceLogRepository.save(logOut);
                }

                // 연차/반차 - LeaveRequest 시드 (이미 있으면 skip)
                if (specialStatus == AttendanceStatus.LEAVE || specialStatus == AttendanceStatus.HALF) {
                    // 반차는 짝수월=오후/홀수월=오전 분기, 시드 typeId 도 같은 기준으로 매핑
                    boolean isAm = specialStatus == AttendanceStatus.HALF && monthVal % 2 == 1;
                    UUID typeId = specialStatus == AttendanceStatus.LEAVE
                            ? annualTypeId
                            : (isAm ? halfAmTypeId : halfPmTypeId);
                    if (typeId != null) {
                        boolean alreadyHas = !leaveRequestRepository
                                .findAllByMemberIdAndStartDateAndDelYn(member.getMemberId(), d, "N")
                                .isEmpty();
                        if (!alreadyHas) {
                            double usage = specialStatus == AttendanceStatus.LEAVE ? 1.0 : 0.5;
                            String reason = specialStatus == AttendanceStatus.LEAVE
                                    ? "연차 사용 (시드)"
                                    : (isAm ? "오전 반차 사용 (시드)" : "오후 반차 사용 (시드)");
                            LocalDateTime requestedAt = d.minusDays(7).atTime(10, 0);
                            LocalDateTime decidedAt = d.minusDays(5).atTime(15, 0);
                            LeaveRequest lr = LeaveRequest.builder()
                                    .memberId(member.getMemberId())
                                    .companyId(companyId)
                                    .companyLeaveTypeId(typeId)
                                    .startDate(d)
                                    .endDate(d)
                                    .usageDays(usage)
                                    .deductedBalanceType(BalanceType.ANNUAL)
                                    .reason(reason)
                                    .approvalStatus(LeaveApprovalStatus.APPROVED)
                                    .requestedBy(member.getMemberId())
                                    .requestedAt(requestedAt)
                                    .decidedBy(SYSTEM_ACTOR)
                                    .decidedAt(decidedAt)
                                    .initiator(LeaveInitiator.SELF)
                                    .build();
                            leaveRequestRepository.save(lr);
                        }
                    }
                }

                // 출장/외근 - WorkTripDetail 추가 저장
                if (specialTripType != null) {
                    String dest = specialTripType == WorkTripType.BUSINESS_TRIP ? "부산 사업장" : "강남 거래처";
                    String purp = specialTripType == WorkTripType.BUSINESS_TRIP ? "분기 출장" : "고객사 외근";
                    WorkTripDetail trip = WorkTripDetail.builder()
                            .memberId(member.getMemberId())
                            .companyId(companyId)
                            .workTripType(specialTripType)
                            .destination(dest)
                            .purpose(purp)
                            .dailyAttendance(savedDaily)
                            .delYn("N")
                            .build();
                    workTripDetailRepository.save(trip);
                }

                memberCreated++;
            }
            totalCreated += memberCreated;
            totalSkipped += memberSkipped;
            log.info("[DEMO-SEED] DailyAttendance 직원 시드 - memberId={} idx={} 생성={} skip={}",
                    member.getMemberId(), idx, memberCreated, memberSkipped);
        }
        log.info("[DEMO-SEED] DailyAttendance 시드 완료 companyId={} 총생성={} 총skip={}",
                companyId, totalCreated, totalSkipped);
    }

    /**
     * 회사 표준 LeavePolicy 1건 - 디폴트 값 (회계연도 기준 / 15일 / 매 2년 +1일 / 25일 cap)
     * 멱등 - 회사에 활성 정책 있으면 skip
     */
    /**
     * 회사별 연차 정책 시드
     * - 회계연도 + 촉진제도 ON: 1/1 일괄 부여, 만료 180/60일 전 촉진 알림, 5일 이월
     * - 입사일 + 촉진제도 OFF: 입사일 기준 부여, 촉진/이월 없음
     */
    private void seedLeavePolicy(UUID companyId, LocalDate effectiveFrom,
                                 AccrualBase accrualBase, boolean usePromotion) {
        boolean exists = !leavePolicyRepository.findByCompanyIdAndDelYn(companyId, "N").isEmpty();
        if (exists) {
            log.info("[DEMO-SEED] LeavePolicy 이미 있음 - skip companyId={}", companyId);
            return;
        }
        LeavePolicy.LeavePolicyBuilder builder = LeavePolicy.builder()
                .companyId(companyId)
                .defaultAnnualDays(15.0)
                .extraDaysPerInterval(1.0)
                .extraIntervalYears(2)
                .maxAnnualDays(25.0)
                .accrualBase(accrualBase);
        if (usePromotion) {
            // 촉진제도 ON + 이월 5일 + 미사용 미지급
            builder.isPromotionYn("Y")
                    .promotion1stBeforeDays(180)
                    .promotion2ndBeforeDays(60)
                    .isCarryoverYn("Y")
                    .carryoverDays(5)
                    .isCarryoverConsentYn("N")
                    .isPayoutYn("N");
        } else {
            // 촉진/이월 모두 OFF (미사용 시 자동 소멸 + 수당 지급 X)
            builder.isPromotionYn("N")
                    .isCarryoverYn("N")
                    .isCarryoverConsentYn("N")
                    .isPayoutYn("N");
        }
        leavePolicyRepository.save(builder.build());
        log.info("[DEMO-SEED] LeavePolicy 시드 완료 companyId={} accrualBase={} promotion={} effectiveFrom={}",
                companyId, accrualBase, usePromotion, effectiveFrom);
    }

    /**
     * 회사 근무 스케줄 시드
     * - flexible=false : FIXED 9-18 / 12-13 점심 / 일 8h
     * - flexible=true  : FLEXIBLE + 시차출퇴근 슬롯 3개 (EARLY/STANDARD/LATE)
     * 멱등 - 회사에 활성 WorkSchedule 있으면 skip
     */
    private void seedWorkSchedule(UUID companyId, java.time.LocalDate effectiveFrom, boolean flexible) {
        boolean exists = workScheduleRepository.findByCompanyIdAndDelYn(companyId, "N")
                .stream()
                .anyMatch(ws -> ws.getMemberId() == null);
        if (exists) {
            log.info("[DEMO-SEED] WorkSchedule 이미 있음 - skip companyId={}", companyId);
            return;
        }
        if (!flexible) {
            // FIXED 9-18, 점심 12-13, 일 8h(=480분)
            WorkSchedule fixed = WorkSchedule.builder()
                    .companyId(companyId)
                    .memberId(null)
                    .scheduleName("기본 근무 (고정)")
                    .workType(WorkType.FIXED)
                    .startTime(java.time.LocalTime.of(9, 0))
                    .endTime(java.time.LocalTime.of(18, 0))
                    .workMinutes(480)
                    .breakStart(java.time.LocalTime.of(12, 0))
                    .breakEnd(java.time.LocalTime.of(13, 0))
                    .effectiveFrom(effectiveFrom)
                    .build();
            workScheduleRepository.save(fixed);
            log.info("[DEMO-SEED] WorkSchedule FIXED 시드 완료 companyId={}", companyId);
            return;
        }

        // FLEXIBLE 1개 + 슬롯 3개
        WorkSchedule flex = workScheduleRepository.save(WorkSchedule.builder()
                .companyId(companyId)
                .memberId(null)
                .scheduleName("시차출퇴근제")
                .workType(WorkType.FLEXIBLE)
                .selectionDeadlineDay(25)
                .effectiveFrom(effectiveFrom)
                .build());

        // EARLY 07:00 - 16:00, 점심 11:00-12:00 (8h=480분)
        flexibleTimeSlotRepository.save(FlexibleTimeSlot.builder()
                .workScheduleId(flex.getWorkScheduleId())
                .companyId(companyId)
                .slotCode("EARLY")
                .slotLabel("조기 출근 (07:00 - 16:00)")
                .startTime(java.time.LocalTime.of(7, 0))
                .endTime(java.time.LocalTime.of(16, 0))
                .workMinutes(480)
                .breakStart(java.time.LocalTime.of(11, 0))
                .breakEnd(java.time.LocalTime.of(12, 0))
                .isDefault(false)
                .activeYn("Y")
                .build());

        // STANDARD 09:00 - 18:00, 점심 12:00-13:00 (8h=480분) - 기본
        flexibleTimeSlotRepository.save(FlexibleTimeSlot.builder()
                .workScheduleId(flex.getWorkScheduleId())
                .companyId(companyId)
                .slotCode("STANDARD")
                .slotLabel("표준 (09:00 - 18:00)")
                .startTime(java.time.LocalTime.of(9, 0))
                .endTime(java.time.LocalTime.of(18, 0))
                .workMinutes(480)
                .breakStart(java.time.LocalTime.of(12, 0))
                .breakEnd(java.time.LocalTime.of(13, 0))
                .isDefault(true)
                .activeYn("Y")
                .build());

        // LATE 11:00 - 20:00, 점심 13:00-14:00 (8h=480분)
        FlexibleTimeSlot lateSlot = flexibleTimeSlotRepository.save(FlexibleTimeSlot.builder()
                .workScheduleId(flex.getWorkScheduleId())
                .companyId(companyId)
                .slotCode("LATE")
                .slotLabel("늦은 출근 (11:00 - 20:00)")
                .startTime(java.time.LocalTime.of(11, 0))
                .endTime(java.time.LocalTime.of(20, 0))
                .workMinutes(480)
                .breakStart(java.time.LocalTime.of(13, 0))
                .breakEnd(java.time.LocalTime.of(14, 0))
                .isDefault(false)
                .activeYn("Y")
                .build());

        log.info("[DEMO-SEED] WorkSchedule FLEXIBLE + 슬롯 3개 시드 완료 companyId={}", companyId);

        // 직원별 슬롯 분배 - idx % 3 -> EARLY/STANDARD/LATE 순환
        // 입사월부터 이번 달까지 매월 MemberScheduleSelection AUTO 상태 시드
        seedFlexibleSlotSelections(companyId, lateSlot);
    }

    /**
     * demo-prev (FLEXIBLE) 직원들에게 시차출퇴근 슬롯 round-robin 분배
     * 입사일 ~ 이번 달까지 매월 MemberScheduleSelection AUTO 상태로 멱등 생성
     */
    private void seedFlexibleSlotSelections(UUID companyId, FlexibleTimeSlot fallbackSlot) {
        ApiResponse<List<MemberResDto>> apiRes;
        try {
            apiRes = memberFeignClient.getMembersByCompany(companyId);
        } catch (Exception e) {
            log.warn("[DEMO-SEED] FlexibleSlotSelection - 직원 조회 실패 - {}", e.getMessage());
            return;
        }
        List<MemberResDto> members = apiRes != null ? apiRes.getData() : List.of();
        if (members.isEmpty()) return;

        // 회사 슬롯 목록 - slotCode 기준 매핑
        List<FlexibleTimeSlot> slots = flexibleTimeSlotRepository.findAll().stream()
                .filter(s -> companyId.equals(s.getCompanyId()) && "Y".equals(s.getActiveYn()))
                .toList();
        FlexibleTimeSlot early = slots.stream().filter(s -> "EARLY".equals(s.getSlotCode())).findFirst().orElse(fallbackSlot);
        FlexibleTimeSlot standard = slots.stream().filter(s -> "STANDARD".equals(s.getSlotCode())).findFirst().orElse(fallbackSlot);
        FlexibleTimeSlot late = slots.stream().filter(s -> "LATE".equals(s.getSlotCode())).findFirst().orElse(fallbackSlot);
        FlexibleTimeSlot[] cycle = { early, standard, late };

        List<MemberResDto> sorted = members.stream()
                .filter(m -> m.getJoinDate() != null)
                .sorted((a, b) -> a.getJoinDate().compareTo(b.getJoinDate()))
                .toList();

        java.time.YearMonth thisMonth = java.time.YearMonth.now();
        int totalCreated = 0;
        int totalSkipped = 0;
        for (int i = 0; i < sorted.size(); i++) {
            MemberResDto m = sorted.get(i);
            FlexibleTimeSlot picked = cycle[i % cycle.length];
            for (java.time.YearMonth ym = java.time.YearMonth.from(m.getJoinDate());
                 !ym.isAfter(thisMonth);
                 ym = ym.plusMonths(1)) {
                String yearMonth = ym.toString(); // YYYY-MM
                if (memberScheduleSelectionRepository
                        .existsByMemberIdAndTargetYearMonthAndApprovalStatus(
                                m.getMemberId(), yearMonth, ScheduleApprovalStatus.APPROVED)
                        || memberScheduleSelectionRepository
                        .existsByMemberIdAndTargetYearMonthAndApprovalStatus(
                                m.getMemberId(), yearMonth, ScheduleApprovalStatus.AUTO)) {
                    totalSkipped++;
                    continue;
                }
                memberScheduleSelectionRepository.save(MemberScheduleSelection.builder()
                        .memberId(m.getMemberId())
                        .companyId(companyId)
                        .targetYearMonth(yearMonth)
                        .slotId(picked.getSlotId())
                        .breakStart(picked.getBreakStart())
                        .breakEnd(picked.getBreakEnd())
                        .approvalStatus(ScheduleApprovalStatus.AUTO)
                        .requestedBy(SYSTEM_ACTOR)
                        .requestedAt(ym.atDay(1).atTime(9, 0))
                        .build());
                totalCreated++;
            }
        }
        log.info("[DEMO-SEED] FlexibleSlotSelection 시드 완료 companyId={} 생성={} skip={}",
                companyId, totalCreated, totalSkipped);
    }

    /**
     * 회사 기본 휴가 종류 17종 시드
     * CompanyLeaveTypeService.initializeDefaults(companyId, null) 호출 - 자체 멱등
     */
    private void seedCompanyLeaveTypes(UUID companyId) {
        try {
            companyLeaveTypeService.initializeDefaults(companyId, null);
            log.info("[DEMO-SEED] CompanyLeaveType 기본 휴가 종류 시드 완료 companyId={}", companyId);
        } catch (Exception e) {
            log.warn("[DEMO-SEED] CompanyLeaveType 시드 실패 - {}", e.getMessage());
        }
    }

    /**
     * OvertimePolicy 자체 멱등 시드
     *  - 활성 정책 (effectiveTo IS NULL OR future) 이미 있으면 skip
     *  - 1.5배 가산 + 야간 22:00~06:00 + 공휴일 사전결재 필수
     *  - 법정: 주 OT 12시간(720분) / 주 총 52시간(3120분)
     *  - 회사 자체: 일 OT 600분(10시간) / 월 OT 2400분(40시간)
     */
    private void seedOvertimePolicy(UUID companyId, LocalDate effectiveFrom) {
        boolean alreadyActive = overtimePolicyRepository
                .findByCompanyIdAndEffectiveToIsNull(companyId)
                .isPresent();
        if (alreadyActive) {
            log.info("[DEMO-SEED] OvertimePolicy 활성 정책 이미 존재 skip companyId={}", companyId);
            return;
        }
        OvertimePolicy overtimePolicy = OvertimePolicy.builder()
                .companyId(companyId)
                .overtimeFloorMinutes(15)
                .approvalMode(ApprovalMode.HYBRID)
                .postApprovalDeadlineHours(72)
                .weeklyOvertimeLimitMinutes(720)
                .weeklyTotalLimitMinutes(3120)
                .dailyOvertimeLimitMinutes(600)
                .monthlyOvertimeLimitMinutes(2400)
                .holidayWorkRequiresApproval(true)
                .effectiveFrom(effectiveFrom)
                .build();
        overtimePolicyRepository.save(overtimePolicy);
        log.info("[DEMO-SEED] OvertimePolicy 시드 완료 companyId={}", companyId);
    }

    /**
     * BonusPolicy 시드 - 회사 유형별 차등
     *
     * 회사 4 (연봉제, demo-current):
     *  - 정기상여 연 400% 4회 (분기당 기본급 100%)
     *  - 성과급 1회 최대 200% (연 1회 평가 기반)
     *  - 명절상여 RATE 50% (설/추석)
     *  - 대상 전직원 / 최소 근속 3개월 / 휴직자 제외
     *
     * 회사 5 (호봉제, demo-prev):
     *  - 정기상여 연 600% 6회 (분기 + 명절 포함)
     *  - 성과급 1회 최대 100% (연 1회)
     *  - 명절상여 AMOUNT 정액 1,000,000원
     *  - 대상 전직원 / 최소 근속 6개월 / 휴직자 제외
     *
     * 멱등: 회사별 활성 정책 1건 이상 있으면 skip
     */
    private void seedBonusPolicy(UUID companyId, boolean usePayGrade, LocalDate effectiveFrom) {
        if (!bonusPolicyRepository.findActivePolicies(companyId, LocalDate.now()).isEmpty()) {
            log.info("[DEMO-SEED] BonusPolicy 이미 활성 - skip companyId={}", companyId);
            return;
        }
        BonusPolicy policy;
        if (usePayGrade) {
            // 호봉제 회사 - 정기상여 비중 큼, 명절 정액
            policy = BonusPolicy.builder()
                    .companyId(companyId)
                    .useRegularBonusYn("Y")
                    .regularBonusAnnualRate(new BigDecimal("600.00"))
                    .regularBonusPaymentCount(6)
                    .usePerformanceBonusYn("Y")
                    .performanceBonusMaxRate(new BigDecimal("100.00"))
                    .performanceBonusBasis("연 1회 평가 등급 기반 - S/A/B/C")
                    .useHolidayBonusYn("Y")
                    .holidayBonusType(HolidayBonusType.AMOUNT)
                    .holidayBonusValue(new BigDecimal("1000000.00"))
                    .eligibilityScope(BonusEligibilityScope.ALL)
                    .minTenureMonths(6)
                    .excludeOnLeaveYn("Y")
                    .effectiveFrom(effectiveFrom)
                    .memo("데모 시드 - 호봉제 표준 보너스 정책")
                    .build();
        } else {
            // 연봉제 회사 - 정기상여 분기당 100%, 명절 비율
            policy = BonusPolicy.builder()
                    .companyId(companyId)
                    .useRegularBonusYn("Y")
                    .regularBonusAnnualRate(new BigDecimal("400.00"))
                    .regularBonusPaymentCount(4)
                    .usePerformanceBonusYn("Y")
                    .performanceBonusMaxRate(new BigDecimal("200.00"))
                    .performanceBonusBasis("연 1회 평가 등급 기반 - S/A/B/C/D")
                    .useHolidayBonusYn("Y")
                    .holidayBonusType(HolidayBonusType.RATE)
                    .holidayBonusValue(new BigDecimal("50.00"))
                    .eligibilityScope(BonusEligibilityScope.ALL)
                    .minTenureMonths(3)
                    .excludeOnLeaveYn("Y")
                    .effectiveFrom(effectiveFrom)
                    .memo("데모 시드 - 연봉제 표준 보너스 정책")
                    .build();
        }
        bonusPolicyRepository.save(policy);
        log.info("[DEMO-SEED] BonusPolicy 시드 완료 companyId={} usePayGrade={} effectiveFrom={}",
                companyId, usePayGrade, effectiveFrom);
    }

    /**
     * 호봉표 시드 - 1~10호봉, 50만원 차이로 누적 (1호봉 250만 ~ 10호봉 700만)
     */
    private void seedPayGradeTable(UUID companyId, LocalDate effectiveFrom) {
        // 1~50호봉, 1호봉 250만 / 호봉 간 100,000원 / 50호봉 = 740만
        long base = 2_500_000L;
        long step = 100_000L;
        for (int i = 1; i <= 50; i++) {
            PayGradeTable grade = PayGradeTable.builder()
                    .companyId(companyId)
                    .step(i)
                    .baseSalary(base + step * (i - 1))
                    .effectiveFrom(effectiveFrom)
                    .build();
            payGradeTableRepository.save(grade);
        }
        log.info("[DEMO-SEED] PayGradeTable 1~50호봉 시드 완료 (250만 ~ 740만, 호봉 간 10만원)");
    }
}
