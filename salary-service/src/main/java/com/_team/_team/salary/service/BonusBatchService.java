package com._team._team.salary.service;

import com._team._team.attendance.domain.MemberLeaveOfAbsence;
import com._team._team.attendance.repository.MemberLeaveOfAbsenceRepository;
import com._team._team.dto.ApiResponse;
import com._team._team.dto.BusinessException;
import com._team._team.salary.domain.BonusPolicy;
import com._team._team.salary.domain.Payroll;
import com._team._team.salary.domain.PayrollItem;
import com._team._team.salary.domain.Salary;
import com._team._team.salary.domain.SalaryItemTemplate;
import com._team._team.salary.domain.enums.BonusEligibilityScope;
import com._team._team.salary.domain.enums.BonusKind;
import com._team._team.salary.domain.enums.HolidayBonusType;
import com._team._team.salary.domain.enums.ItemType;
import com._team._team.salary.domain.enums.PayrollStatus;
import com._team._team.salary.domain.enums.PayrollType;
import com._team._team.salary.dto.reqdto.BonusBatchReqDto;
import com._team._team.salary.dto.resdto.BonusBatchApplyResDto;
import com._team._team.salary.dto.resdto.BonusBatchPreviewResDto;
import com._team._team.salary.feignClients.MemberFeignClient;
import com._team._team.salary.feignClients.dto.MemberResDto;
import com._team._team.salary.repository.BonusPolicyRepository;
import com._team._team.salary.repository.PayrollItemRepository;
import com._team._team.salary.repository.PayrollRepository;
import com._team._team.salary.repository.SalaryItemTemplateRepository;
import com._team._team.salary.repository.SalaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 보너스 일괄 발행 - 회사 단위 시뮬 미리보기 + 명세서 발행
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class BonusBatchService {

    private final BonusPolicyRepository bonusPolicyRepository;
    private final SalaryRepository salaryRepository;
    private final PayrollRepository payrollRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final SalaryItemTemplateRepository salaryItemTemplateRepository;
    private final MemberFeignClient memberFeignClient;
    // 휴직자 제외 정책 검증용
    private final MemberLeaveOfAbsenceRepository memberLeaveOfAbsenceRepository;
    // 상여금 명세서 발행 후 4대보험·소득세 자동 라인 재계산용
    private final PayrollService payrollService;

    @Autowired
    public BonusBatchService(
            BonusPolicyRepository bonusPolicyRepository,
            SalaryRepository salaryRepository,
            PayrollRepository payrollRepository,
            PayrollItemRepository payrollItemRepository,
            SalaryItemTemplateRepository salaryItemTemplateRepository,
            MemberFeignClient memberFeignClient,
            MemberLeaveOfAbsenceRepository memberLeaveOfAbsenceRepository,
            PayrollService payrollService
    ) {
        this.bonusPolicyRepository = bonusPolicyRepository;
        this.salaryRepository = salaryRepository;
        this.payrollRepository = payrollRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.salaryItemTemplateRepository = salaryItemTemplateRepository;
        this.memberFeignClient = memberFeignClient;
        this.memberLeaveOfAbsenceRepository = memberLeaveOfAbsenceRepository;
        this.payrollService = payrollService;
    }

    public BonusBatchPreviewResDto preview(UUID companyId, BonusBatchReqDto req) {
        BonusPolicy policy = findActivePolicyOrThrow(companyId, req.getPayDate());
        validateRequest(policy, req);

        // 동일 종류 충돌만 차단
        PayrollType payrollType = resolvePayrollType(req.getBonusKind());
        List<MemberResDto> members = fetchMembers(companyId);
        // 휴직자 검증용 - excludeOnLeaveYn=Y 정책에서만 사용, 빈 Map 이면 검증 skip
        Map<UUID, MemberLeaveOfAbsence> loaMap = "Y".equals(policy.getExcludeOnLeaveYn())
                ? loadActiveLoaMap(companyId, req.getPayDate())
                : Map.of();
        List<BonusBatchPreviewResDto.TargetEntry> targets = new ArrayList<>();
        long total = 0L;
        int skipped = 0;

        for (MemberResDto m : members) {
            // 자격 미달 사유 체크
            String skipReason = checkEligibility(policy, req, m, loaMap);
            // baseSalary 조회
            Optional<Salary> activeSalary = salaryRepository.findActiveSalary(
                    m.getMemberId(), companyId, req.getPayDate());
            if (activeSalary.isEmpty() && skipReason == null) {
                skipReason = "활성 급여 정보 없음";
            }
            // 다른 종류(정기상여 vs 성과급)는 같은 날 허용
            if (skipReason == null) {
                Optional<Payroll> conflict = payrollRepository
                        .findByCompanyIdAndMemberIdAndPayrollYearMonthDayAndPayrollType(
                                companyId, m.getMemberId(), req.getPayDate(), payrollType);
                if (conflict.isPresent()) {
                    skipReason = String.format("같은 날 %s 이미 발행됨", resolveItemName(req.getBonusKind()));
                }
            }
            long baseSalary = activeSalary.map(s -> s.getBaseSalary() == null ? 0L : s.getBaseSalary()).orElse(0L);

            long bonusAmount = 0L;
            boolean exceedsLimit = false;
            if (skipReason == null) {
                bonusAmount = computeBonusAmount(req, policy, baseSalary);
                exceedsLimit = isOverLimit(req, policy, baseSalary, bonusAmount);
            }

            if (skipReason != null) skipped++;
            else total += bonusAmount;

            targets.add(BonusBatchPreviewResDto.TargetEntry.builder()
                    .memberId(m.getMemberId())
                    .name(m.getName())
                    .sabun(m.getSabun())
                    .organizationName(m.getOrganizationName())
                    .baseSalary(baseSalary)
                    .bonusAmount(bonusAmount)
                    .exceedsLimit(exceedsLimit)
                    .skipReason(skipReason)
                    .build());
        }

        return BonusBatchPreviewResDto.builder()
                .bonusKind(req.getBonusKind())
                .payDate(req.getPayDate())
                .policyMaxRate(resolveLimitRate(policy, req.getBonusKind()))
                .appliedRate(req.getRatePercent() != null ? req.getRatePercent().doubleValue() : null)
                .totalEligible(targets.size() - skipped)
                .totalSkipped(skipped)
                .totalGrossAmount(total)
                .targets(targets)
                .build();
    }

    @Transactional
    public BonusBatchApplyResDto apply(UUID companyId, BonusBatchReqDto req) {
        BonusPolicy policy = findActivePolicyOrThrow(companyId, req.getPayDate());
        validateRequest(policy, req);

        PayrollType payrollType = resolvePayrollType(req.getBonusKind());
        String itemName = resolveItemName(req.getBonusKind());
        // 회사 SalaryItemTemplate에 동일 itemName 있으면 과세/통상임금 정보 차용
        SalaryItemTemplate template = salaryItemTemplateRepository
                .findByCompanyIdAndItemNameAndDelYn(companyId, itemName, "N")
                .orElse(null);
        String taxableYn = template != null ? template.getIsTaxableYn() : "Y";

        // 직원 목록 + memberId -> Member
        List<MemberResDto> allMembers = fetchMembers(companyId);
        Map<UUID, MemberResDto> memberMap = new HashMap<>();
        for (MemberResDto m : allMembers) {
            memberMap.put(m.getMemberId(), m);
        }

        // 발행 대상 결정
        // - items 있으면 차등 모드 - 행별 비율 우선
        // - items 없으면 일괄 모드
        boolean isItemized = req.getItems() != null && !req.getItems().isEmpty();
        List<UUID> targetMemberIds = new ArrayList<>();
        Map<UUID, BigDecimal> rateOverrides = new HashMap<>();
        if (isItemized) {
            for (BonusBatchReqDto.MemberItem mi : req.getItems()) {
                if (mi.getMemberId() == null) continue;
                if (mi.getRatePercent() == null
                        || mi.getRatePercent().compareTo(BigDecimal.ZERO) <= 0) continue;
                targetMemberIds.add(mi.getMemberId());
                rateOverrides.put(mi.getMemberId(), mi.getRatePercent());
            }
        } else {
            for (MemberResDto m : allMembers) targetMemberIds.add(m.getMemberId());
        }

        // 휴직자 검증용 Map - 정책에 휴직자 제외 옵션이 켜진 경우만 조회
        Map<UUID, MemberLeaveOfAbsence> loaMap = "Y".equals(policy.getExcludeOnLeaveYn())
                ? loadActiveLoaMap(companyId, req.getPayDate())
                : Map.of();

        List<UUID> created = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (UUID memberId : targetMemberIds) {
            MemberResDto m = memberMap.get(memberId);
            if (m == null) {
                failures.add(memberId + ": 직원 정보 없음");
                continue;
            }
            try {
                // 자격 검증 (휴직자 포함)
                String skip = checkEligibility(policy, req, m, loaMap);
                if (skip != null) {
                    failures.add(String.format("%s: %s", m.getName(), skip));
                    continue;
                }

                Salary salary = salaryRepository.findActiveSalary(
                                memberId, companyId, req.getPayDate())
                        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "활성 급여 정보 없음"));
                long baseSalary = salary.getBaseSalary() == null ? 0L : salary.getBaseSalary();

                // 충돌 - 같은 (직원 + 지급일 + payrollType) 명세서
                Optional<Payroll> existing = payrollRepository
                        .findByCompanyIdAndMemberIdAndPayrollYearMonthDayAndPayrollType(
                                companyId, memberId, req.getPayDate(), payrollType);
                if (existing.isPresent()) {
                    failures.add(String.format("%s: 같은 날 %s 이미 발행됨", m.getName(), itemName));
                    continue;
                }

                // 산출 - 차등 모드면 행별 비율 우선
                BigDecimal effRate = rateOverrides.getOrDefault(memberId, req.getRatePercent());
                long bonusAmount = computeBonusAmountWithRate(req.getBonusKind(), effRate, policy, baseSalary);
                if (bonusAmount <= 0) continue;

                String targetYearMonth = req.getPayDate().getYear() + "-"
                        + String.format("%02d", req.getPayDate().getMonthValue());
                Payroll payroll = Payroll.builder()
                        .companyId(companyId)
                        .memberId(memberId)
                        .salaryId(salary.getSalaryId())
                        .payrollYearMonthDay(req.getPayDate())
                        .targetYearMonth(targetYearMonth)
                        .payrollStatus(PayrollStatus.DRAFT)
                        .payrollType(payrollType)
                        .totalPayment(bonusAmount)
                        .totalDeduction(0L)
                        .netPay(bonusAmount)
                        .delYn("N")
                        .build();
                Payroll saved = payrollRepository.save(payroll);

                PayrollItem item = PayrollItem.builder()
                        .payroll(saved)
                        .itemName(itemName)
                        .itemType(ItemType.EARNING)
                        .amount(bonusAmount)
                        .displayOrder(10)
                        .isTaxableYn(taxableYn)
                        .delYn("N")
                        .build();
                payrollItemRepository.save(item);

                // 상여금의 4대보험, 소득세 자동  생성
                payrollItemRepository.flush();
                payrollService.applyAutoTaxItems(saved.getPayrollId());

                created.add(saved.getPayrollId());
            } catch (Exception e) {
                String msg = String.format("%s: %s", m.getName(), e.getMessage());
                failures.add(msg);
                log.warn("[BONUS-BATCH] 발행 실패 - {}", msg);
            }
        }
        log.info("[BONUS-BATCH] 일괄 발행 완료 companyId={} kind={} payDate={} itemized={} created={} failed={}",
                companyId, req.getBonusKind(), req.getPayDate(), isItemized, created.size(), failures.size());
        return BonusBatchApplyResDto.builder()
                .created(created.size())
                .failed(failures.size())
                .payrollIds(created)
                .failures(failures)
                .build();
    }

    // ─────────────────────── helpers ───────────────────────

    private BonusPolicy findActivePolicyOrThrow(UUID companyId, LocalDate refDate) {
        return bonusPolicyRepository.findActivePolicies(companyId, refDate)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "활성 보너스 정책이 없습니다. [상여/성과금 정책] 메뉴에서 정책을 먼저 등록하세요."));
    }

    private void validateRequest(BonusPolicy policy, BonusBatchReqDto req) {
        if (req.getPayDate() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "지급일을 입력하세요.");
        }
        if (req.getBonusKind() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "보너스 유형을 선택하세요.");
        }
        boolean hasItems = req.getItems() != null && !req.getItems().isEmpty();
        switch (req.getBonusKind()) {
            case REGULAR -> {
                if (!"Y".equals(policy.getUseRegularBonusYn())) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "정기상여가 비활성화된 정책입니다.");
                }
                // 일괄 모드면 ratePercent 필수, 차등(items) 모드면 행별 비율 사용
                if (!hasItems && req.getRatePercent() == null) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "정기상여 지급 비율(%)을 입력하세요.");
                }
            }
            case PERFORMANCE -> {
                if (!"Y".equals(policy.getUsePerformanceBonusYn())) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "성과급이 비활성화된 정책입니다.");
                }
                if (!hasItems && req.getRatePercent() == null) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "성과급 지급 비율(%)을 입력하세요.");
                }
            }
            case HOLIDAY -> {
                if (!"Y".equals(policy.getUseHolidayBonusYn())) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "명절상여가 비활성화된 정책입니다.");
                }
            }
        }
    }

    private String checkEligibility(BonusPolicy policy, BonusBatchReqDto req, MemberResDto m,
                                    Map<UUID, MemberLeaveOfAbsence> loaMap) {
        if (m.getJoinDate() == null) return "입사일 미등록";
        long monthsServed = ChronoUnit.MONTHS.between(m.getJoinDate(), req.getPayDate());
        Integer minMonths = policy.getMinTenureMonths();
        if (minMonths != null && minMonths > 0 && monthsServed < minMonths) {
            return String.format("최소 근속 %d개월 미만", minMonths);
        }
        if (policy.getEligibilityScope() == BonusEligibilityScope.REGULAR_ONLY) {
            String t = m.getEmploymentType();
            // FULL_TIME / 정규직 외엔 제외
            if (t == null || !(t.equalsIgnoreCase("FULL_TIME") || t.contains("정규"))) {
                return "정규직 아님";
            }
        }
        // 휴직자 제외 정책 - 지급날 시점 활성 휴직 중인 직원 차단
        if ("Y".equals(policy.getExcludeOnLeaveYn())
                && loaMap != null && loaMap.containsKey(m.getMemberId())) {
            return "휴직 중";
        }
        return null;
    }

    // 회사 + 지급날 활성 휴직자 Map - 매 호출 시 1회 조회 (preview/apply 각자)
    private Map<UUID, MemberLeaveOfAbsence> loadActiveLoaMap(UUID companyId, LocalDate payDate) {
        return memberLeaveOfAbsenceRepository
                .findAllActiveInCompanyOnDate(companyId, payDate)
                .stream()
                .collect(Collectors.toMap(
                        MemberLeaveOfAbsence::getMemberId,
                        loa -> loa,
                        (a, b) -> a));
    }

    private long computeBonusAmount(BonusBatchReqDto req, BonusPolicy policy, long baseSalary) {
        return computeBonusAmountWithRate(req.getBonusKind(), req.getRatePercent(), policy, baseSalary);
    }

    // 직원별 차등 비율(override) 적용용 - REGULAR/PERFORMANCE만 의미 있음, HOLIDAY는 정책값 그대로
    private long computeBonusAmountWithRate(BonusKind kind, BigDecimal rate, BonusPolicy policy, long baseSalary) {
        return switch (kind) {
            case REGULAR, PERFORMANCE -> {
                if (rate == null) yield 0L;
                yield BigDecimal.valueOf(baseSalary)
                        .multiply(rate)
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                        .longValueExact();
            }
            case HOLIDAY -> {
                if (policy.getHolidayBonusType() == HolidayBonusType.AMOUNT) {
                    yield policy.getHolidayBonusValue() == null ? 0L
                            : policy.getHolidayBonusValue().longValueExact();
                } else { // RATE
                    BigDecimal r = policy.getHolidayBonusValue();
                    if (r == null) yield 0L;
                    yield BigDecimal.valueOf(baseSalary)
                            .multiply(r)
                            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                            .longValueExact();
                }
            }
        };
    }

    private boolean isOverLimit(BonusBatchReqDto req, BonusPolicy policy, long baseSalary, long bonusAmount) {
        if (req.getBonusKind() != BonusKind.PERFORMANCE) return false;
        BigDecimal max = policy.getPerformanceBonusMaxRate();
        if (max == null) return false;
        long maxAmount = BigDecimal.valueOf(baseSalary)
                .multiply(max)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValueExact();
        return bonusAmount > maxAmount;
    }

    private Double resolveLimitRate(BonusPolicy policy, BonusKind kind) {
        return switch (kind) {
            case PERFORMANCE -> policy.getPerformanceBonusMaxRate() == null ? null
                    : policy.getPerformanceBonusMaxRate().doubleValue();
            case REGULAR -> policy.getRegularBonusAnnualRate() == null ? null
                    : policy.getRegularBonusAnnualRate().doubleValue();
            case HOLIDAY -> policy.getHolidayBonusValue() == null ? null
                    : policy.getHolidayBonusValue().doubleValue();
        };
    }

    private PayrollType resolvePayrollType(BonusKind kind) {
        return kind == BonusKind.PERFORMANCE ? PayrollType.PERFORMANCE_BONUS : PayrollType.SPECIAL_BONUS;
    }

    private String resolveItemName(BonusKind kind) {
        return switch (kind) {
            case PERFORMANCE -> "성과급";
            case REGULAR -> "정기상여";
            case HOLIDAY -> "명절상여";
        };
    }

    private List<MemberResDto> fetchMembers(UUID companyId) {
        try {
            ApiResponse<List<MemberResDto>> res = memberFeignClient.getMembersByCompany(companyId);
            return res != null && res.getData() != null ? res.getData() : List.of();
        } catch (Exception e) {
            log.warn("[BONUS-BATCH] 직원 목록 조회 실패 - {}", e.getMessage());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "직원 목록 조회 실패");
        }
    }
}
