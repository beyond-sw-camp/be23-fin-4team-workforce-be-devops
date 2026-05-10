package com._team._team.salary.service;

import com._team._team.attendance.domain.MonthlyAttendanceLedger;
import com._team._team.dto.BusinessException;
import com._team._team.salary.domain.enums.AllowanceApprovalStatus;
import com._team._team.salary.domain.enums.TaxCategory;
import com._team._team.salary.repository.MemberAllowanceRepository;
import com._team._team.salary.repository.PayrollItemRepository;
import com._team._team.salary.repository.PayrollRepository;
import com._team._team.salary.repository.SalaryItemTemplateRepository;
import com._team._team.salary.repository.SalaryRepository;
import com._team._team.salary.domain.*;
import com._team._team.salary.domain.enums.ItemType;
import com._team._team.salary.domain.enums.PayrollStatus;
import com._team._team.salary.domain.enums.PayrollType;
import com._team._team.salary.domain.enums.ProrationMethod;
import com._team._team.salary.domain.vo.AutoPayrollItem;
import com._team._team.salary.domain.vo.SettlementPeriod;
import com._team._team.salary.dto.reqdto.PayrollCreateReqDto;
import com._team._team.salary.dto.reqdto.PayrollItemCreateReqDto;
import com._team._team.salary.dto.reqdto.PayrollItemUpdateReqDto;
import com._team._team.salary.dto.reqdto.PayrollUpdateReqDto;
import com._team._team.salary.dto.resdto.BulkPayrollActionResDto;
import com._team._team.salary.dto.resdto.MyAnnualSalaryResDto;
import com._team._team.salary.dto.resdto.PayrollAdminListResDto;
import com._team._team.salary.dto.resdto.AllowanceMonthlyResDto;
import com._team._team.salary.dto.resdto.PayrollItemResDto;
import com._team._team.salary.dto.resdto.PayrollResDto;
import com._team._team.salary.feignClients.MemberFeignClient;
import com._team._team.salary.feignClients.dto.MemberResDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com._team._team.attendance.domain.MemberLeaveOfAbsence;
import com._team._team.attendance.domain.enums.LeaveOfAbsenceApprovalStatus;
import com._team._team.attendance.repository.MemberLeaveOfAbsenceRepository;
import com._team._team.dto.NotificationMessage;
import com._team._team.notification.NotificationType;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;


@Slf4j
@Service
@Transactional
public class PayrollService {

    private final PayrollRepository payrollRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final SalaryRepository salaryRepository;
    private final SalaryItemTemplateRepository salaryItemTemplateRepository;
    private final PayrollCalculationService payrollCalculationService;
    private final MemberAllowanceService memberAllowanceService;
    private final MemberLeaveOfAbsenceRepository memberLeaveOfAbsenceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CachedMemberLookupService cachedMemberLookup;
    private final MemberAllowanceRepository memberAllowanceRepository;

    @Autowired
    public PayrollService(PayrollRepository payrollRepository,
                          PayrollItemRepository payrollItemRepository,
                          SalaryRepository salaryRepository,
                          SalaryItemTemplateRepository salaryItemTemplateRepository,
                          PayrollCalculationService payrollCalculationService,
                          MemberAllowanceService memberAllowanceService,
                          MemberLeaveOfAbsenceRepository memberLeaveOfAbsenceRepository,
                          ApplicationEventPublisher eventPublisher,
                          CachedMemberLookupService cachedMemberLookup,
                          MemberAllowanceRepository memberAllowanceRepository){
        this.payrollRepository = payrollRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.salaryRepository = salaryRepository;
        this.salaryItemTemplateRepository = salaryItemTemplateRepository;
        this.payrollCalculationService = payrollCalculationService;
        this.memberAllowanceService = memberAllowanceService;
        this.memberLeaveOfAbsenceRepository = memberLeaveOfAbsenceRepository;
        this.eventPublisher = eventPublisher;
        this.cachedMemberLookup = cachedMemberLookup;
        this.memberAllowanceRepository = memberAllowanceRepository;
    }

    /**
     * 급여대장 생성
     */
    public PayrollResDto createPayroll(UUID companyId, PayrollCreateReqDto reqDto){

        /** 1. 직원의 정산기간 기준 적용된 급여 조회
         *  - 1차: 정산일(payrollDate) 시점에 active 인 행 (정상 케이스)
         *  - 2차: 정산일 시점엔 종료됐지만 정산기간 일부와 겹친 행 (월 중간 퇴직자)
         */
        SettlementPeriod settlementPeriod = payrollCalculationService
                .calculateSettlementPeriod(companyId, reqDto.getPayrollYearMonthDay());
        Salary salary = salaryRepository.findActiveSalary(
                        reqDto.getMemberId(), companyId, reqDto.getPayrollYearMonthDay())
                .or(() -> salaryRepository.findSalariesOverlappingPeriod(
                                reqDto.getMemberId(), companyId,
                                settlementPeriod.from(), settlementPeriod.to())
                        .stream().findFirst())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "해당 직원의 적용 중인 급여 정보를 찾을 수 없습니다."));

        /**
         * 2. 직원 + 정산일 기존 이력 체크
         * delYn=N: 이미 활성 행, 중복 차단, delYn=Y: 이전 소프트 삭제된 행, hard delete 후 재생성 허용
         */
        Optional<Payroll> existing = payrollRepository
                .findByCompanyIdAndMemberIdAndPayrollYearMonthDay(
                        companyId, reqDto.getMemberId(), reqDto.getPayrollYearMonthDay());
        if (existing.isPresent()) {
            Payroll prev = existing.get();
            if ("N".equals(prev.getDelYn())) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "해당 직원의 동일 정산일에 이미 급여대장이 존재합니다.");
            }
            // 소프트 삭제된 행 정리 - 같은 unique key 재사용 가능하게 hard delete + flush
            payrollRepository.delete(prev);
            payrollRepository.flush();
        }

        /** 3. 정산기간 (1단계에서 이미 계산된 것 재사용) */
        SettlementPeriod period = settlementPeriod;

        /** 3-1. 무급 휴직 일수 기반 기본급 비례율 계산 */
        int unpaidLeaveDays = calculateUnpaidLeaveDays(companyId, reqDto.getMemberId(), period);
        // 활성 급여정책 조회 일할계산 방식 / 월 소정근로시간 같이 결정
        SalaryPolicy activePolicyForRatio = payrollCalculationService.findActivePolicy(
                companyId, reqDto.getPayrollYearMonthDay());
        double paidRatio = calculatePaidRatio(period, salary, unpaidLeaveDays, activePolicyForRatio);

        /** 4. Payroll 생성 (금액은 0으로 초기화, 항목 생성 후 재계산)
         *     targetYearMonth = SalaryPolicy.payCycleType 기준 정산 대상 월 (YYYY-MM)
         *     활성 정책 없으면 fallback - 지급일이 속한 월 (CURRENT_MONTH 가정) */
        String targetYearMonth = activePolicyForRatio != null
                ? activePolicyForRatio.resolveTargetYearMonth(reqDto.getPayrollYearMonthDay()).toString()
                : java.time.YearMonth.from(reqDto.getPayrollYearMonthDay()).toString();
        Payroll payroll = reqDto.toEntity(companyId, salary.getSalaryId(), targetYearMonth);
        Payroll saved;
        try {
            saved = payrollRepository.saveAndFlush(payroll);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "동시에 동일한 급여대장 생성 요청이 감지되었습니다. 잠시 후 다시 시도해주세요.");
        }

        /** 5. 회사의 급여 항목 템플릿 조회 -> PayrollItem 일괄 생성 */
        List<SalaryItemTemplate> templates = salaryItemTemplateRepository
                .findByCompanyIdAndDelYn(companyId, "N");

        // 5-1. 정산일 기준 활성 수당(APPROVED, AUTO) 조회
        List<MemberAllowance> activeAllowances = memberAllowanceService.findActiveAt(
                reqDto.getMemberId(), companyId, reqDto.getPayrollYearMonthDay());
        Map<UUID, Long> allowanceByTemplate = activeAllowances.stream()
                .collect(Collectors.toMap(
                        MemberAllowance::getSalaryItemTemplateId,
                        MemberAllowance::getAmount,
                        (a, b) -> a));

        long totalPayment = 0L;
        long totalTaxableEarning = 0L;   // 과세 대상 EARNING 합계 (4대보험/소득세 기준액)
        long totalDeduction = 0L;
        // 통상임금 합계 시급 환산 기준 가산수당 base
        long ordinaryWage = (long) Math.floor(salary.getBaseSalary() * paidRatio);
        List<PayrollItem> payrollItems = new ArrayList<>();

        for (SalaryItemTemplate template : templates) {
             // 모든 EARNING 에 동일한 paidRatio (일할 + 무급휴직) 적용해서 정합성 유지
             // opt-in 모델 - 기본급 외엔 모두 MemberAllowance 명시 부여 직원만 적용 (자동 적용 X)
            long amount = 0L;
            if ("기본급".equals(template.getItemName())) {
                amount = (long) Math.floor(salary.getBaseSalary() * paidRatio);
            } else if (template.getItemType() == ItemType.EARNING) {
                Long allowanceAmount = allowanceByTemplate.get(template.getSalaryItemTemplateId());
                if (allowanceAmount != null) {
                    amount = (long) Math.floor(allowanceAmount * paidRatio);
                }
                // 기본급 외 EARNING 인데 부여 안 된 항목 (amount=0)은 라인 박지 않음 - opt-in 모델
                if (amount == 0) continue;
            }

            PayrollItem payItem = PayrollItem.fromTemplate(saved, template, amount);
            payrollItems.add(payItem);

            // EARNING/DEDUCTION 분류하여 합계 누적
            if (template.getItemType() == ItemType.EARNING) {
                totalPayment += amount;
                // 통상임금 플래그 Y 면 시급 환산 기준에 합산 기본급은 이미 포함되어 있어 중복 가산 방지
                if ("Y".equals(template.getIsOrdinaryWageYn())
                        && !"기본급".equals(template.getItemName())) {
                    ordinaryWage += amount;
                }
                // 과세 항목만 과세 총액에 누적 (비과세 식대, 비과세 교통비 등 제외)
                if ("Y".equals(template.getIsTaxableYn())) {
                    // 전액 과세
                    totalTaxableEarning += amount;
                } else {
                    // 비과세 항목, 한도 초과분은 과세 가산
                    TaxCategory category = template.getTaxCategory();
                    if (category != null) {
                        Long limit = category.getMonthlyNonTaxableLimit();
                        if (limit != null && amount > limit) {
                            totalTaxableEarning += (amount - limit);
                        }
                    }
                }
            } else {
                totalDeduction += amount;
            }
        }
        /**
         * 6. 월 장부 조회 (
         * 기본급 + 수당 + 4대보험 만으로 정산 진행
         */
        YearMonth targetMonth = YearMonth.from(period.from());
        Optional<MonthlyAttendanceLedger> ledgerOpt = payrollCalculationService
                .findLedgerOptional(reqDto.getMemberId(), targetMonth);
        if (ledgerOpt.isEmpty()) {
            log.info("[PAYROLL] 월 장부 없음 - 출퇴근 기반 가산/공제 스킵. memberId={} yearMonth={}",
                    reqDto.getMemberId(), targetMonth);
        }

        /** 7. 활성 급여정책 재사용 (위에서 일할 계산용으로 이미 조회) */
        SalaryPolicy activePolicy = activePolicyForRatio;

        // 월 장부 있는 경우만 출퇴근 기반 자동 항목 계산
        if (ledgerOpt.isPresent()) {
            MonthlyAttendanceLedger ledger = ledgerOpt.get();

            /** 7-1. 초과근무수당 자동 계산 */
            AutoPayrollItem overtimeItem = payrollCalculationService.calculateOvertimePay(
                    ledger, ordinaryWage, activePolicy);
            if (overtimeItem != null){
                PayrollItem payItem = buildPayrollItem(saved, overtimeItem);
                payrollItems.add(payItem);
                totalPayment += overtimeItem.amount();
                if ("Y".equals(overtimeItem.isTaxableYn())) {
                    totalTaxableEarning += overtimeItem.amount();
                }
            }

            /** 8. 공휴일근무수당 자동 계산 */
            AutoPayrollItem holidayItem = payrollCalculationService.calculateHolidayWorkPay(
                    ledger, ordinaryWage, activePolicy);
            if(holidayItem != null){
                PayrollItem payrollItem = buildPayrollItem(saved, holidayItem);
                payrollItems.add(payrollItem);
                totalPayment += holidayItem.amount();
                if ("Y".equals(holidayItem.isTaxableYn())) {
                    totalTaxableEarning += holidayItem.amount();
                }
            }

            /** 8-1. 야간근무수당 자동 계산 (가산분 0.5배) */
            AutoPayrollItem nightItem = payrollCalculationService.calculateNightWorkPay(
                    ledger, ordinaryWage, activePolicy);
            if(nightItem != null){
                PayrollItem payrollItem = buildPayrollItem(saved, nightItem);
                payrollItems.add(payrollItem);
                totalPayment += nightItem.amount();
                if ("Y".equals(nightItem.isTaxableYn())) {
                    totalTaxableEarning += nightItem.amount();
                }
            }

            /** 8-2-pre. 지각·조퇴 공제 자동 계산 */
            AutoPayrollItem tardyItem = payrollCalculationService.calculateTardyEarlyLeaveDeduction(
                    ledger, ordinaryWage, activePolicy);
            if (tardyItem != null) {
                PayrollItem payrollItem = buildPayrollItem(saved, tardyItem);
                payrollItems.add(payrollItem);
                totalDeduction += tardyItem.amount();
            }

            /** 8-2. 결근 공제, 주휴수당 포함 */
            AutoPayrollItem absentItem = payrollCalculationService.calculateAbsentDeduction(
                    ledger, ordinaryWage, activePolicy);
            if (absentItem != null) {
                PayrollItem payrollItem = buildPayrollItem(saved, absentItem);
                payrollItems.add(payrollItem);
                totalDeduction += absentItem.amount();
            }
        }

        /**
         * 9. 과세 총지급액 기준 4대보험 + 세금 자동 공제
         * totalTaxableEarning: 비과세 제외 -> 실제 과세 대상 금액
         */
        // 정산 시점에 감면 기간이 만료됐는지도 함께 판정 - 만료 후엔 감면율 0 으로 적용.
        java.math.BigDecimal effectiveReductionRate =
                salary.isTaxReductionApplicableAt(reqDto.getPayrollYearMonthDay())
                        ? salary.getTaxReductionRate()
                        : null;
        List<AutoPayrollItem> deductionItems = payrollCalculationService.calculateDeductions(
                totalTaxableEarning,
                reqDto.getPayrollYearMonthDay(),
                salary.getDependentCount() == null ? 1 : salary.getDependentCount(),
                salary.getChildUnder20Count() == null ? 0 : salary.getChildUnder20Count(),
                effectiveReductionRate);

        for(AutoPayrollItem deduction : deductionItems){
            PayrollItem payrollItem = buildPayrollItem(saved ,deduction);
            payrollItems.add(payrollItem);
            totalDeduction += deduction.amount();
        }

        /** 10. saveAll로 배치 저장 */
        payrollItemRepository.saveAll(payrollItems);
        saved.recalculate(totalPayment, totalDeduction);

        return PayrollResDto.fromEntity(saved);
    }

    /**
     * AutoPayrollItem(값 객체) -> PayrollItem(엔티티) 변환
     */
    private PayrollItem buildPayrollItem(Payroll payroll, AutoPayrollItem autoItem) {
        return PayrollItem.builder()
                .payroll(payroll)
                .itemName(autoItem.itemName())
                .itemType(autoItem.itemType())
                .amount(autoItem.amount())
                .displayOrder(autoItem.displayOrder())
                .isTaxableYn(autoItem.isTaxableYn())
                .build();
    }

    /**
     * 급여대장 단건 조회
     */
    @Transactional(readOnly = true)
    public PayrollResDto findPayrollById(UUID companyId, UUID payrollId) {
        Payroll payroll = payrollRepository.findByPayrollIdAndCompanyIdAndDelYn(payrollId, companyId, "N")
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "급여 대장을 찾을 수 없습니다."));
        return PayrollResDto.fromEntity(payroll);
    }

    /**
     * 직원별 급여대장 목록 조회
     */
    @Transactional(readOnly = true)
    public List<PayrollResDto> findPayrollByMemberId(UUID companyId, UUID memberId) {
        return payrollRepository.findByMemberIdAndCompanyIdAndDelYnOrderByPayrollYearMonthDayDesc(memberId, companyId, "N").stream()
                .map(PayrollResDto::fromEntity)
                .toList();
    }

    /**
     * 급여대장 수정 (DRAFT 상태에서만)
     * 확정, 지급 상태에서는 수정 불가
     */
    public PayrollResDto updatePayroll(UUID companyId, UUID payrollId, PayrollUpdateReqDto reqDto){
        Payroll payroll = payrollRepository.findByPayrollIdAndCompanyIdAndDelYn(payrollId, companyId, "N")
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "급여 대장을 찾을 수 없습니다."));

        if(!payroll.isModifiable()){
            throw new BusinessException(HttpStatus.BAD_REQUEST, "확정된 급여대장은 수정할 수 없습니다.");
        }

        payroll.update(reqDto.getPayrollYearMonthDay(),
                reqDto.getTotalPayment(), reqDto.getTotalDeduction(), reqDto.getNetPay());

        return PayrollResDto.fromEntity(payroll);
    }

    /**
     * 급여대장 소프트 삭제 (DRAFT 상태에서만)
     */
    public void deletePayroll(UUID companyId, UUID payrollId){
        Payroll payroll = payrollRepository.findByPayrollIdAndCompanyIdAndDelYn(payrollId, companyId, "N")
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "급여 대장을 찾을 수 없습니다."));

        if(!payroll.isModifiable()){
            throw new BusinessException(HttpStatus.BAD_REQUEST, "확정된 급여대장은 삭제할 수 없습니다.");
        }

        payroll.delete();
    }

    /**
     * 급여 확정 (DRAFT -> CONFIRMED)
     */
    public PayrollResDto confirmPayroll(UUID companyId, UUID payrollId){
        Payroll payroll = payrollRepository.findByPayrollIdAndCompanyIdAndDelYn(payrollId, companyId, "N")
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "급여 대장을 찾을 수 없습니다."));

        if(payroll.getPayrollStatus() != PayrollStatus.DRAFT){
            throw new BusinessException(HttpStatus.BAD_REQUEST, "확정 전인 상태에서만 수정할 수 있습니다.");
        }

        payroll.confirm();

        // 직원에게 명세서 발행 알림
        eventPublisher.publishEvent(NotificationMessage.builder()
                .receiverId(payroll.getMemberId())
                .senderId(null)
                .notificationType(NotificationType.SALARY_PUBLISHED)
                .content("[" + payroll.getPayrollYearMonthDay() + "] 급여명세서가 발행되었습니다.")
                .targetId(payroll.getPayrollId())
                .targetType("PAYROLL")
                .build());
        return PayrollResDto.fromEntity(payroll);
    }

    /**
     * 지급 완료 (CONFIRMED -> PAID)
     */
    public PayrollResDto payPayroll(UUID companyId, UUID payrollId){
        Payroll payroll = payrollRepository.findByPayrollIdAndCompanyIdAndDelYn(payrollId, companyId, "N")
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "급여 대장을 찾을 수 없습니다."));

        if (payroll.getPayrollStatus() != PayrollStatus.CONFIRMED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "확정 상태에서만 지급처리 가능합니다.");
        }

        payroll.pay();

        // 직원에게 지급 완료 알림
        eventPublisher.publishEvent(NotificationMessage.builder()
                .receiverId(payroll.getMemberId())
                .senderId(null)
                .notificationType(NotificationType.SALARY_PAID)
                .content("[" + payroll.getPayrollYearMonthDay() + "] 급여 "
                        + String.format("%,d", payroll.getNetPay()) + "원이 지급되었습니다.")
                .targetId(payroll.getPayrollId())
                .targetType("PAYROLL")
                .build());

        return PayrollResDto.fromEntity(payroll);
    }

    // 회사 단위 처리 필요(지급전, 확정 상태인 급여) 급여대장 시간 무관 조회
    // 정산 처리 화면 데이터 (중요) - 이 화면에서 급여(정기급여, 퇴직, 상여, 성과) 모두 처리
    @Transactional(readOnly = true)
    public List<PayrollAdminListResDto> listPendingByCompany(UUID companyId) {
        List<Payroll> payrolls = payrollRepository
                .findByCompanyIdAndPayrollStatusInAndDelYnOrderByPayrollYearMonthDayAsc(
                        companyId,
                        List.of(PayrollStatus.DRAFT, PayrollStatus.CONFIRMED),
                        "N");
        if (payrolls.isEmpty()) return Collections.emptyList();

        Map<UUID, MemberResDto> memberMap = fetchMemberMap(companyId);
        return payrolls.stream()
                .map(p -> {
                    MemberResDto m = memberMap.get(p.getMemberId());
                    String sabun = m != null ? m.getSabun() : null;
                    String name = m != null ? m.getName() : null;
                    String orgName = m != null ? m.getOrganizationName() : null;
                    return PayrollAdminListResDto.fromEntity(p, sabun, name, orgName);
                })
                .toList();
    }

    // 회사 월 단위 급여대장 전체 조회 직원 정보 결합
    @Transactional(readOnly = true)
    public List<PayrollAdminListResDto> listByCompanyAndMonth(UUID companyId, YearMonth ym) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        // QueryDSL 기반 동적 검색으로 통합
        List<Payroll> payrolls = payrollRepository.searchAdminListInMonth(
                companyId, from, to, null, null, null);
        if (payrolls.isEmpty()) {
            return Collections.emptyList();
        }

        Map<UUID, MemberResDto> memberMap = fetchMemberMap(companyId);

        return payrolls.stream()
                .map(p -> {
                    MemberResDto m = memberMap.get(p.getMemberId());
                    String sabun = m != null ? m.getSabun() : null;
                    String name = m != null ? m.getName() : null;
                    String orgName = m != null ? m.getOrganizationName() : null;
                    return PayrollAdminListResDto.fromEntity(p, sabun, name, orgName);
                })
                .toList();
    }

    // 일괄 확정 행별 try catch 한 명 실패해도 다른 행 진행
    public BulkPayrollActionResDto bulkConfirm(UUID companyId, List<UUID> payrollIds) {
        int success = 0;
        int fail = 0;
        List<String> failures = new ArrayList<>();

        for (UUID id : payrollIds) {
            try {
                confirmPayroll(companyId, id);
                success++;
            } catch (BusinessException e) {
                fail++;
                failures.add(id + " - " + e.getMessage());
            } catch (Exception e) {
                fail++;
                failures.add(id + " - 예상치 못한 오류");
            }
        }

        log.info("[PAYROLL-BULK-CONFIRM] companyId={} success={} fail={}",
                companyId, success, fail);

        return BulkPayrollActionResDto.builder()
                .success(success)
                .fail(fail)
                .failures(failures)
                .build();
    }

    // 일괄 지급 처리
    public BulkPayrollActionResDto bulkPay(UUID companyId, List<UUID> payrollIds) {
        int success = 0;
        int fail = 0;
        List<String> failures = new ArrayList<>();

        for (UUID id : payrollIds) {
            try {
                payPayroll(companyId, id);
                success++;
            } catch (BusinessException e) {
                fail++;
                failures.add(id + " - " + e.getMessage());
            } catch (Exception e) {
                fail++;
                failures.add(id + " - 예상치 못한 오류");
            }
        }

        log.info("[PAYROLL-BULK-PAY] companyId={} success={} fail={}",
                companyId, success, fail);

        return BulkPayrollActionResDto.builder()
                .success(success)
                .fail(fail)
                .failures(failures)
                .build();
    }

    // 직원 본인 연간 급여 집계 연봉조회 화면용
    // 월별 행 12개 고정 (없는 달은 0) - 정산 대상 월(targetYearMonth) 기준
    // 항목별 누적 (지급 / 공제 분리)
    @Transactional(readOnly = true)
    public MyAnnualSalaryResDto findMyAnnual(UUID companyId, UUID memberId, int year) {
        // 전월형 회사의 1월분 (2월 10일 지급) 도 정확히 1월 행에 묶임
        String fromYm = String.format("%04d-01", year);
        String toYm = String.format("%04d-12", year);
        List<Payroll> payrolls = payrollRepository
                .findByTargetYearMonthRangeFetchItems(companyId, memberId, fromYm, toYm);

        // 월별 행 1 ~ 12 빈 슬롯 먼저 생성
        Map<Integer, MyAnnualSalaryResDto.MonthlyRow> monthly = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            monthly.put(m, MyAnnualSalaryResDto.MonthlyRow.builder()
                    .month(m)
                    .payrollId(null)
                    .payrollYearMonthDay(null)
                    .payrollStatus(null)
                    .totalPayment(0L)
                    .totalDeduction(0L)
                    .netPay(0L)
                    .build());
        }

        // 항목 누적 LinkedHashMap 사용 화면 표시 순서 유지
        Map<String, Long> earnSum = new LinkedHashMap<>();
        Map<String, Long> dedSum = new LinkedHashMap<>();

        long totalPayment = 0L;
        long totalDeduction = 0L;
        long netPay = 0L;
        int payrollCount = 0;

        for (Payroll p : payrolls) {
            // 정산 대상 월(targetYearMonth) 기준으로 매핑. 누락 시 fallback
            int month = p.getTargetYearMonth() != null
                    ? Integer.parseInt(p.getTargetYearMonth().substring(5))
                    : p.getPayrollYearMonthDay().getMonthValue();
            long pay = nz(p.getTotalPayment());
            long ded = nz(p.getTotalDeduction());
            long net = nz(p.getNetPay());

            // 같은 달 여러 건이면 누적
            MyAnnualSalaryResDto.MonthlyRow prev = monthly.get(month);
            MyAnnualSalaryResDto.MonthlyRow merged = MyAnnualSalaryResDto.MonthlyRow.builder()
                    .month(month)
                    .payrollId(p.getPayrollId() != null ? p.getPayrollId().toString() : null)
                    .payrollYearMonthDay(p.getPayrollYearMonthDay() != null
                            ? p.getPayrollYearMonthDay().toString() : null)
                    .targetYearMonth(p.getTargetYearMonth())
                    .payrollStatus(p.getPayrollStatus())
                    .totalPayment(prev.getTotalPayment() + pay)
                    .totalDeduction(prev.getTotalDeduction() + ded)
                    .netPay(prev.getNetPay() + net)
                    .build();
            monthly.put(month, merged);

            totalPayment += pay;
            totalDeduction += ded;
            netPay += net;
            payrollCount++;

            if (p.getPayrollItemList() != null) {
                for (PayrollItem item : p.getPayrollItemList()) {
                    if (item == null || "Y".equals(item.getDelYn())) continue;
                    long amt = nz(item.getAmount());
                    if (item.getItemType() == ItemType.EARNING) {
                        earnSum.merge(item.getItemName(), amt, Long::sum);
                    } else if (item.getItemType() == ItemType.DEDUCTION) {
                        dedSum.merge(item.getItemName(), amt, Long::sum);
                    }
                }
            }
        }

        long monthlyAverage = payrollCount > 0 ? netPay / payrollCount : 0L;

        return MyAnnualSalaryResDto.builder()
                .year(year)
                .monthly(new ArrayList<>(monthly.values()))
                .earnings(toBreakdown(earnSum))
                .deductions(toBreakdown(dedSum))
                .totalPayment(totalPayment)
                .totalDeduction(totalDeduction)
                .netPay(netPay)
                .monthlyAverage(monthlyAverage)
                .payrollCount(payrollCount)
                .build();
    }

    private static long nz(Long v) {
        return v != null ? v : 0L;
    }

    private static List<MyAnnualSalaryResDto.ItemBreakdown> toBreakdown(Map<String, Long> sum) {
        List<MyAnnualSalaryResDto.ItemBreakdown> rows = new ArrayList<>();
        for (Map.Entry<String, Long> e : sum.entrySet()) {
            rows.add(MyAnnualSalaryResDto.ItemBreakdown.builder()
                    .itemName(e.getKey())
                    .totalAmount(e.getValue())
                    .build());
        }
        return rows;
    }

    // 회사 직원 정보 일괄 조회 헬퍼 - Redis 캐시(5분 TTL) 활용
    private Map<UUID, MemberResDto> fetchMemberMap(UUID companyId) {
        return cachedMemberLookup.getMembersByCompany(companyId).stream()
                .collect(Collectors.toMap(
                        MemberResDto::getMemberId, m -> m, (a, b) -> a));
    }

    // 급여대장 항목

    /**
     * 급여대장 항목 추가
     * DRAFT 상태에서만 항목 추가 가능
     */
    public PayrollItemResDto createPayrollItem(UUID companyId, UUID payrollId, PayrollItemCreateReqDto reqDto){
        Payroll payroll = payrollRepository.findByPayrollIdAndCompanyIdAndDelYn(payrollId, companyId, "N")
                .orElseThrow(()-> new BusinessException(HttpStatus.NOT_FOUND, "급여대장을 찾을 수 없습니다."));

        if(!payroll.isModifiable()){
            throw new BusinessException(HttpStatus.BAD_REQUEST,"확정된 급여대장에는 항목을 추가할 수 없습니다.");
        }

        /** 템플릿 조회 (항목명, 유형 스냅샷용) */
        SalaryItemTemplate template = salaryItemTemplateRepository.findById(reqDto.getSalaryItemTemplateId())
                .orElseThrow(()-> new BusinessException(HttpStatus.NOT_FOUND, "정의된 급여 항목을 찾을 수 없습니다."));

        /** 동일 항목명 중복 체크 (같은 급여대장 내 삭제되지 않은 항목 기준) */
        if (payrollItemRepository.existsByPayroll_PayrollIdAndItemNameAndDelYn(
                payrollId, template.getItemName(), "N")) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "이미 동일한 항목('" + template.getItemName() + "')이 존재합니다.");
        }

        /** PayrollItem 생성 (템플릿에서 항목명, 유형 복사) */
        PayrollItem payrollItem = reqDto.toEntity(payroll, template);
        PayrollItem saved = payrollItemRepository.save(payrollItem);

        // 4대보험/소득세 자동 재계산, 그 외엔 합계만
        if (template.getItemType() == ItemType.EARNING) {
            recalculateAutoTaxItems(payroll);
        } else {
            recalculatePayroll(payroll);
        }

        return PayrollItemResDto.fromEntity(saved);
    }

    /**
     * 회사 그 월 직원별 수당 집계 (회사 공통 + 개인 차등 모두 포함)
     * 명세서 있으면 PayrollItem 기반, 없으면 MemberAllowance + 회사 공통으로 예정 데이터 합성
     */
    @Transactional(readOnly = true)
    public List<AllowanceMonthlyResDto> findMonthlyAllowanceByCompany(
            UUID companyId, YearMonth yearMonth) {
        LocalDate from = yearMonth.atDay(1);
        LocalDate to = yearMonth.atEndOfMonth();

        List<PayrollItem> lines = payrollItemRepository.findAllowanceLinesInMonth(
                companyId, from, to,
                ItemType.EARNING,
                PayrollType.REGULAR_MONTHLY,
                ALLOWANCE_EXCLUDE_NAMES);

        List<SalaryItemTemplate> companyTpls = salaryItemTemplateRepository
                .findByCompanyIdAndDelYn(companyId, "N");

        if (!lines.isEmpty()) {
            return buildFromPayrollItems(lines, companyTpls);
        }
        return buildPreviewFromAllowances(companyId, from, to, companyTpls);
    }

    /** PayrollItem 기반 - 정산 명세서가 있는 경우 정확한 지급 데이터 */
    private List<AllowanceMonthlyResDto> buildFromPayrollItems(
            List<PayrollItem> lines, List<SalaryItemTemplate> companyTpls) {
        Map<String, String> applyAllByName = companyTpls.stream()
                .collect(Collectors.toMap(SalaryItemTemplate::getItemName,
                        t -> t.getFixedAmountYn() == null ? "N" : t.getFixedAmountYn(),
                        (a, b) -> a));
        Map<String, UUID> tplIdByName = companyTpls.stream()
                .collect(Collectors.toMap(SalaryItemTemplate::getItemName,
                        SalaryItemTemplate::getSalaryItemTemplateId, (a, b) -> a));

        Map<UUID, List<PayrollItem>> byMember = new LinkedHashMap<>();
        for (PayrollItem pi : lines) {
            byMember.computeIfAbsent(pi.getPayroll().getMemberId(), k -> new ArrayList<>()).add(pi);
        }

        // memberId + templateId 키로 그 월 active 였던 MemberAllowance 매핑
        // (라인별 close 액션 + effectiveTo 표시용 - 종료된 행도 포함하려고 month 범위로 조회)
        Map<String, UUID> allowanceIdByKey = new HashMap<>();
        Map<String, LocalDate> effectiveToByKey = new HashMap<>();
        if (!byMember.isEmpty()) {
            UUID companyId = lines.get(0).getPayroll().getCompanyId();
            LocalDate refDate = lines.get(0).getPayroll().getPayrollYearMonthDay();
            LocalDate monthStart = refDate.withDayOfMonth(1);
            LocalDate monthEnd = refDate.withDayOfMonth(refDate.lengthOfMonth());
            List<MemberAllowance> monthActive = memberAllowanceRepository
                    .findCompanyAllowancesActiveInMonth(companyId, monthStart, monthEnd);
            for (MemberAllowance a : monthActive) {
                if (a.getSalaryItemTemplateId() == null) continue;
                if (a.getApprovalStatus() != AllowanceApprovalStatus.APPROVED
                        && a.getApprovalStatus() != AllowanceApprovalStatus.AUTO) continue;
                String key = a.getMemberId() + "::" + a.getSalaryItemTemplateId();
                allowanceIdByKey.putIfAbsent(key, a.getMemberAllowanceId());
                if (a.getEffectiveTo() != null) {
                    effectiveToByKey.putIfAbsent(key, a.getEffectiveTo());
                }
            }
        }

        List<AllowanceMonthlyResDto> result = new ArrayList<>();
        for (Map.Entry<UUID, List<PayrollItem>> e : byMember.entrySet()) {
            UUID memberId = e.getKey();
            List<PayrollItem> items = e.getValue();
            PayrollStatus status = items.get(0).getPayroll().getPayrollStatus();
            long total = 0L;
            List<AllowanceMonthlyResDto.Line> lineDtos = new ArrayList<>();
            for (PayrollItem pi : items) {
                long amt = pi.getAmount() == null ? 0L : pi.getAmount();
                total += amt;
                boolean isCommon = "Y".equals(applyAllByName.get(pi.getItemName()));
                boolean isTaxFree = "N".equals(pi.getIsTaxableYn());
                UUID memberAllowanceId = null;
                LocalDate effectiveTo = null;
                if (!isCommon) {
                    UUID tplId = tplIdByName.get(pi.getItemName());
                    if (tplId != null) {
                        String key = memberId + "::" + tplId;
                        memberAllowanceId = allowanceIdByKey.get(key);
                        effectiveTo = effectiveToByKey.get(key);
                    }
                }
                lineDtos.add(AllowanceMonthlyResDto.Line.builder()
                        .payrollItemId(pi.getPayrollItemId())
                        .memberAllowanceId(memberAllowanceId)
                        .effectiveTo(effectiveTo)
                        .itemName(pi.getItemName())
                        .amount(amt)
                        .isCommon(isCommon)
                        .isTaxFree(isTaxFree)
                        .build());
            }
            result.add(AllowanceMonthlyResDto.builder()
                    .memberId(memberId)
                    .payrollStatus(status)
                    .items(lineDtos)
                    .totalAmount(total)
                    .build());
        }
        return result;
    }

    /**
     * MemberAllowance + 회사 공통 자동 항목으로 예정 데이터 합성
     * 정산 명세서 없을 때 (자동 배치 전 또는 미생성) 부여 데이터로 미리보기
     * - payrollStatus = null 로 표시 (FE 가 "정산 전 예정" 라벨)
     * - payrollItemId = null 로 라인 단위 삭제 차단
     */
    private List<AllowanceMonthlyResDto> buildPreviewFromAllowances(
            UUID companyId, LocalDate from, LocalDate to,
            List<SalaryItemTemplate> companyTpls) {
        // opt-in 모델 - 모든 적용은 MemberAllowance 명시 부여로만 (자동 적용 X)
        // 그 월 active MemberAllowance 만으로 예정 라인 합성

        // 회사 활성 직원 조회 (Feign 캐시 사용)
        List<MemberResDto> members;
        try {
            members = cachedMemberLookup.getMembersByCompany(companyId);
            if (members == null) members = List.of();
        } catch (Exception e) {
            log.warn("[ALLOWANCE-PREVIEW] 직원 list 조회 실패 - {}", e.getMessage());
            members = List.of();
        }

        // 그 월 active MemberAllowance (회사 공통 + 개인 차등 모두)
        // 영구 종료(effectiveTo <= today) 라인은 미리보기에서 즉시 제외 - 명세서 미생성 달도 정리됨
        LocalDate today = LocalDate.now();
        List<MemberAllowance> allowances = memberAllowanceRepository
                .findCompanyAllowancesActiveInMonth(companyId, from, to);
        Map<UUID, List<MemberAllowance>> allowanceByMember = allowances.stream()
                .filter(a -> a.getApprovalStatus() == AllowanceApprovalStatus.APPROVED
                        || a.getApprovalStatus() == AllowanceApprovalStatus.AUTO)
                .filter(a -> a.getEffectiveTo() == null
                        || a.getEffectiveTo().isAfter(today))
                .collect(Collectors.groupingBy(MemberAllowance::getMemberId));

        Map<UUID, SalaryItemTemplate> tplById = companyTpls.stream()
                .collect(Collectors.toMap(SalaryItemTemplate::getSalaryItemTemplateId,
                        t -> t, (a, b) -> a));

        List<AllowanceMonthlyResDto> result = new ArrayList<>();
        for (MemberResDto member : members) {
            UUID memberId = member.getMemberId();
            if (memberId == null) continue;

            List<AllowanceMonthlyResDto.Line> lineDtos = new ArrayList<>();
            long total = 0L;

            // 부여된 수당 라인 - 고정 항목(fixedAmountYn=Y)이든 자유 항목이든 모두 명시 부여만
            List<MemberAllowance> active = allowanceByMember.getOrDefault(memberId, List.of());
            for (MemberAllowance a : active) {
                SalaryItemTemplate t = tplById.get(a.getSalaryItemTemplateId());
                if (t == null) continue;
                if (ALLOWANCE_EXCLUDE_NAMES.contains(t.getItemName())) continue;
                long amt = a.getAmount() == null ? 0L : a.getAmount();
                total += amt;
                boolean isFixed = "Y".equals(t.getFixedAmountYn());
                lineDtos.add(AllowanceMonthlyResDto.Line.builder()
                        .payrollItemId(null)
                        .memberAllowanceId(a.getMemberAllowanceId())
                        .effectiveTo(a.getEffectiveTo())
                        .itemName(t.getItemName())
                        .amount(amt)
                        .isCommon(isFixed)
                        .isTaxFree("N".equals(t.getIsTaxableYn()))
                        .build());
            }

            if (lineDtos.isEmpty()) continue;

            result.add(AllowanceMonthlyResDto.builder()
                    .memberId(memberId)
                    .payrollStatus(null)
                    .items(lineDtos)
                    .totalAmount(total)
                    .build());
        }
        return result;
    }

    // 수당 집계에서 제외할 항목명 - 기본급 / 상여류 / 퇴직성 / 미사용 연차 수당
    private static final List<String> ALLOWANCE_EXCLUDE_NAMES = List.of(
            "기본급", "정기상여", "성과급", "명절상여",
            "퇴직금", "퇴직월 일할 급여", "미사용 연차 수당"
    );

    /**
     * 급여대장별 항목 목록 조회 (삭제되지 않은 것만)
     */
    @Transactional(readOnly = true)
    public List<PayrollItemResDto> findPayrollItems(UUID companyId, UUID payrollId) {
        payrollRepository.findByPayrollIdAndCompanyIdAndDelYn(payrollId, companyId, "N")
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "급여대장을 찾을 수 없습니다."));

        return payrollItemRepository.findByPayroll_PayrollIdAndDelYnOrderByDisplayOrder(payrollId, "N")
                .stream().map(PayrollItemResDto::fromEntity).toList();
    }

    /**
     * 급여대장 항목 수정
     * DRAFT 상태에서만 수정 가능, 금액,정렬순서 수정
     * 수정 후 Payroll 합계 자동 재계산
     */
    public PayrollItemResDto updatePayrollItem(UUID companyId, UUID payrollItemId, PayrollItemUpdateReqDto reqDto){
        PayrollItem item = payrollItemRepository.findById(payrollItemId)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "급여대장 항목을 찾을 수 없습니다."));

        /** 소속 급여대장의 회사 검증 */
        if (!item.getPayroll().getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        if(!item.getPayroll().isModifiable()){
            throw new BusinessException(HttpStatus.BAD_REQUEST, "확정된 급여대장의 항목은 수정 불가합니다.");
        }

        item.updateAmount(reqDto.getAmount());
        item.updateDisplayOrder(reqDto.getDisplayOrder());

        // EARNING 수정 시 4대보험/소득세 자동 재계산, 그 외엔 합계만
        if (item.getItemType() == ItemType.EARNING) {
            recalculateAutoTaxItems(item.getPayroll());
        } else {
            recalculatePayroll(item.getPayroll());
        }

        return PayrollItemResDto.fromEntity(item);
    }

    /**
     * 급여대장 항목 소프트 삭제 (DRAFT 상태에서만)
     */
    public void deletePayrollItem(UUID companyId, UUID payrollItemId){
        PayrollItem item = payrollItemRepository.findById(payrollItemId)
                .orElseThrow(()-> new BusinessException(HttpStatus.NOT_FOUND, "급여 대장을 찾을 수 없습니다."));

        /** 소속 급여대장의 회사 검증 */
        if (!item.getPayroll().getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        if(!item.getPayroll().isModifiable()){
            throw new BusinessException(HttpStatus.BAD_REQUEST, "확정된 급여대장의 항목은 삭제 불가합니다.");
        }

        item.delete();

        // 4대보험/소득세 자동 재계산, 그 외엔 합계만
        if (item.getItemType() == ItemType.EARNING) {
            recalculateAutoTaxItems(item.getPayroll());
        } else {
            recalculatePayroll(item.getPayroll());
        }
    }

    /**
     * 수당 영구 종료 시 호출, 그 직원의 미지급(DRAFT) Payroll 들에서 itemName 일치하는 라인 일괄 soft delete
     * - DRAFT 만 대상, CONFIRMED/PAID 명세서는 보존
     * - delYn='N' 활성 라인만, 이미 종료된 라인 중복 처리 방지
     * - 4대보험/소득세 자동 재계산까지 수행
     */
    public void deleteAllowanceItemsForMember(UUID companyId, UUID memberId, String itemName) {
        if (itemName == null || itemName.isBlank()) return;

        List<Payroll> drafts = payrollRepository
                .findByMemberIdAndPayrollStatusAndCompanyIdAndDelYn(
                        memberId, PayrollStatus.DRAFT, companyId, "N");

        for (Payroll payroll : drafts) {
            if (!payroll.isModifiable()) continue;
            List<PayrollItem> targets = payroll.getPayrollItemList().stream()
                    .filter(it -> "N".equals(it.getDelYn()))
                    .filter(it -> itemName.equals(it.getItemName()))
                    .toList();
            for (PayrollItem it : targets) {
                it.delete();
            }
            // 라인 변동 시 EARNING 이면 4대보험·소득세 자동 라인까지 재산출, 아니면 합계만
            boolean hasEarningChange = targets.stream()
                    .anyMatch(it -> it.getItemType() == ItemType.EARNING);
            if (hasEarningChange) {
                recalculateAutoTaxItems(payroll);
            } else if (!targets.isEmpty()) {
                recalculatePayroll(payroll);
            }
        }
    }

    // 자동 산출 4대보험·소득세 라인 항목명 - 수동 추가/수정/삭제 시 재계산 대상
    private static final Set<String> AUTO_TAX_ITEM_NAMES = Set.of(
            "국민연금", "건강보험", "장기요양보험", "고용보험", "소득세", "지방소득세"
    );

    /**
     *  4대보험, 소득세 자동 라인 재산출
     * - 보너스 일괄 발행에서 사용
     */
    public void applyAutoTaxItems(UUID payrollId) {
        Payroll payroll = payrollRepository.findById(payrollId).orElse(null);
        if (payroll == null) return;
        if (!payroll.isModifiable()) return;
        recalculateAutoTaxItems(payroll);
    }

    /**
     * EARNING(과세 또는 비과세 한도 초과) 변동 시 4대보험·소득세 자동 라인 재산출
     * - 기존 자동 라인 hard delete 후 calculateDeductions 결과로 재생성
     * - 종료 후 합계 재계산까지 수행
     */
    private void recalculateAutoTaxItems(Payroll payroll) {
        UUID payrollId = payroll.getPayrollId();

        // 1. 현재 라인 조회 후 자동 공제 라인 식별 / hard delete
        List<PayrollItem> all = payrollItemRepository
                .findByPayroll_PayrollIdAndDelYnOrderByDisplayOrder(payrollId, "N");
        List<PayrollItem> autoLines = all.stream()
                .filter(p -> AUTO_TAX_ITEM_NAMES.contains(p.getItemName()))
                .toList();
        if (!autoLines.isEmpty()) {
            payrollItemRepository.deleteAll(autoLines);
            payrollItemRepository.flush();
        }

        // 2. 비과세 한도 판정용 템플릿 맵
        Map<String, SalaryItemTemplate> tplByName = salaryItemTemplateRepository
                .findByCompanyIdAndDelYn(payroll.getCompanyId(), "N").stream()
                .collect(Collectors.toMap(SalaryItemTemplate::getItemName, t -> t, (a, b) -> a));

        // 3. totalTaxableEarning 재산출 - 자동 공제 제외
        long totalTaxableEarning = 0L;
        for (PayrollItem item : all) {
            if (AUTO_TAX_ITEM_NAMES.contains(item.getItemName())) continue;
            if (item.getItemType() != ItemType.EARNING) continue;
            if ("Y".equals(item.getIsTaxableYn())) {
                totalTaxableEarning += item.getAmount();
            } else {
                SalaryItemTemplate tpl = tplByName.get(item.getItemName());
                if (tpl != null && tpl.getTaxCategory() != null) {
                    Long limit = tpl.getTaxCategory().getMonthlyNonTaxableLimit();
                    if (limit != null && item.getAmount() > limit) {
                        totalTaxableEarning += (item.getAmount() - limit);
                    }
                }
            }
        }

        // 4. 활성 Salary 조회 - 부양가족/자녀/감면율
        Salary salary = salaryRepository.findActiveSalary(
                payroll.getMemberId(), payroll.getCompanyId(),
                payroll.getPayrollYearMonthDay()).orElse(null);
        if (salary == null) {
            log.warn("[PAYROLL] 자동세금 재계산 - 활성 Salary 없음 payrollId={}", payrollId);
            recalculatePayroll(payroll);
            return;
        }
        BigDecimal effectiveRate = salary.isTaxReductionApplicableAt(payroll.getPayrollYearMonthDay())
                ? salary.getTaxReductionRate() : null;

        // 5. calculateDeductions 호출 후 신규 PayrollItem 저장
        List<AutoPayrollItem> deductions = payrollCalculationService.calculateDeductions(
                totalTaxableEarning,
                payroll.getPayrollYearMonthDay(),
                salary.getDependentCount() == null ? 1 : salary.getDependentCount(),
                salary.getChildUnder20Count() == null ? 0 : salary.getChildUnder20Count(),
                effectiveRate);

        List<PayrollItem> newItems = deductions.stream()
                .map(d -> buildPayrollItem(payroll, d))
                .toList();
        if (!newItems.isEmpty()) {
            payrollItemRepository.saveAll(newItems);
        }

        // 6. 합계 재계산
        recalculatePayroll(payroll);
    }

    /**
     * Payroll 합계 재계산
     */
    private void recalculatePayroll(Payroll payroll) {
        Long totalPayment = payrollItemRepository
                .sumAmountByPayrollIdAndItemType(payroll.getPayrollId(), ItemType.EARNING);
        Long totalDeduction = payrollItemRepository
                .sumAmountByPayrollIdAndItemType(payroll.getPayrollId(), ItemType.DEDUCTION);

        payroll.recalculate(totalPayment, totalDeduction);
    }

    /**
     * 정산기간 내 무급 휴직 일수 합산
     * ACTIVE 는 endDate, ENDED 는 actualEndDate 기준으로 실제 기간 산정
     */
    private int calculateUnpaidLeaveDays(UUID companyId, UUID memberId, SettlementPeriod period) {
        List<MemberLeaveOfAbsence> leaveOfAbsences = memberLeaveOfAbsenceRepository
                .findInPeriod(companyId, memberId, period.to());

        int unpaidDays = 0;
        for (MemberLeaveOfAbsence loa : leaveOfAbsences) {
            if (!"N".equals(loa.getIsPaidYn())) {
                continue;   // 유급 휴직 (출산/육아/군복무 등) 은 차감 안 함
            }

            // 실제 종료일, ENDED 면 actualEndDate, 그 외 endDate
            LocalDate effectiveEnd =
                    loa.getStatus() == LeaveOfAbsenceApprovalStatus.ENDED
                            && loa.getActualEndDate() != null
                            ? loa.getActualEndDate()
                            : loa.getEndDate();

            // 정산기간 밖에서 끝난 건 제외
            if (effectiveEnd.isBefore(period.from())) {
                continue;
            }

            // 정산기간과 겹치는 부분만 계산
            LocalDate overlapStart = loa.getStartDate().isBefore(period.from())
                    ? period.from() : loa.getStartDate();
            LocalDate overlapEnd = effectiveEnd.isAfter(period.to())
                    ? period.to() : effectiveEnd;

            if (!overlapStart.isAfter(overlapEnd)) {
                unpaidDays += (int) ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1;
            }
        }
        return unpaidDays;
    }

    /**
     * 실근무일 비율 정산기간 대비 근무일 / 분모
     *  - Salary.effectiveFrom 이 period 시작 후 중도 입사 일할
     *  - Salary.effectiveTo 가 period 종료 전 중도 퇴사 일할
     *  - 무급휴직일 추가 차감
     *  - 분모는 정책 ProrationMethod 따라 분기
     *    DAYS_IN_MONTH 해당월 일수 (28~31)
     *    FIXED_30 30일 고정 (통상임금 표준)
     *    WORKING_DAYS 월 소정근로일 (간이 22일 사용 회사 영업일 카운트는 향후 보강)
     */
    private double calculatePaidRatio(SettlementPeriod period,
                                      Salary salary,
                                      int unpaidLeaveDays,
                                      SalaryPolicy policy) {
        LocalDate periodStart = period.from();
        LocalDate periodEnd = period.to();
        int periodDays = (int) ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        if (periodDays <= 0) return 1.0;

        LocalDate workStart = salary.getEffectiveFrom().isAfter(periodStart)
                ? salary.getEffectiveFrom()
                : periodStart;

        LocalDate workEnd = (salary.getEffectiveTo() != null && salary.getEffectiveTo().isBefore(periodEnd))
                ? salary.getEffectiveTo()
                : periodEnd;

        if (workStart.isAfter(workEnd)) return 0.0;

        int workDays = (int) ChronoUnit.DAYS.between(workStart, workEnd) + 1;
        workDays = Math.max(0, workDays - unpaidLeaveDays);

        // 정책 분기 분모 결정
        ProrationMethod method =
                policy != null && policy.getProrationMethod() != null
                        ? policy.getProrationMethod()
                        : ProrationMethod.DAYS_IN_MONTH;

        int denominator;
        switch (method) {
            case FIXED_30 -> denominator = 30;
            case WORKING_DAYS -> denominator = 22;  // 간이 22일 향후 회사 영업일 캘린더 연동
            case DAYS_IN_MONTH -> denominator = periodDays;
            default -> denominator = periodDays;
        }

        // 비율은 1.0 을 넘지 않도록 캡 (FIXED_30 에서 31일 월에 발생할 수 있음)
        double ratio = (double) workDays / denominator;
        return Math.min(ratio, 1.0);
    }
}