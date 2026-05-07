package com._team._team.salary.service;

import com._team._team.dto.ApiResponse;
import com._team._team.dto.BusinessException;
import com._team._team.salary.domain.BonusPolicy;
import com._team._team.salary.domain.Payroll;
import com._team._team.salary.domain.PayrollItem;
import com._team._team.salary.domain.Salary;
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
import com._team._team.salary.repository.SalaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 보너스 일괄 발행 - 회사 단위 시뮬 미리보기 + 명세서 발행
 *
 * 흐름:
 *  1) preview - 활성 BonusPolicy + 직원 목록으로 대상 / 산출액 계산 (DB 변경 X)
 *  2) apply   - 각 직원별 PayrollType=PERFORMANCE_BONUS / SPECIAL_BONUS 명세서 DRAFT 생성
 *
 * 항목 매핑:
 *  - PERFORMANCE -> PERFORMANCE_BONUS / 항목명 "성과급"
 *  - REGULAR     -> SPECIAL_BONUS    / 항목명 "정기상여"
 *  - HOLIDAY     -> SPECIAL_BONUS    / 항목명 "명절상여"
 *
 * 산출 공식:
 *  - REGULAR : baseSalary * ratePercent / 100  (정책 연 누계 / 지급횟수 prefill 권장)
 *  - PERFORMANCE : baseSalary * ratePercent / 100 (정책 max 한도 검증)
 *  - HOLIDAY (RATE) : baseSalary * holidayBonusValue / 100
 *  - HOLIDAY (AMOUNT) : holidayBonusValue (정액)
 *
 * 자격 검증:
 *  - eligibilityScope: REGULAR_ONLY 면 employmentType=FULL_TIME 만
 *  - minTenureMonths : joinDate ~ payDate 사이 개월 수 미만이면 스킵
 *  - 활성 Salary baseSalary > 0 이어야 함
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BonusBatchService {

    private final BonusPolicyRepository bonusPolicyRepository;
    private final SalaryRepository salaryRepository;
    private final PayrollRepository payrollRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final MemberFeignClient memberFeignClient;

    public BonusBatchPreviewResDto preview(UUID companyId, BonusBatchReqDto req) {
        BonusPolicy policy = findActivePolicyOrThrow(companyId, req.getPayDate());
        validateRequest(policy, req);

        List<MemberResDto> members = fetchMembers(companyId);
        List<BonusBatchPreviewResDto.TargetEntry> targets = new ArrayList<>();
        long total = 0L;
        int skipped = 0;

        for (MemberResDto m : members) {
            // 자격 미달 사유 체크
            String skipReason = checkEligibility(policy, req, m);
            // baseSalary 조회
            Optional<Salary> activeSalary = salaryRepository.findActiveSalary(
                    m.getMemberId(), companyId, req.getPayDate());
            if (activeSalary.isEmpty() && skipReason == null) {
                skipReason = "활성 급여 정보 없음";
            }
            // 같은 (직원 + 지급일)에 다른 명세서 이미 있으면 발행 불가 - DB unique constraint 충돌 사전 차단
            if (skipReason == null) {
                Optional<Payroll> conflict = payrollRepository
                        .findByCompanyIdAndMemberIdAndPayrollYearMonthDay(
                                companyId, m.getMemberId(), req.getPayDate());
                if (conflict.isPresent()) {
                    String existingType = conflict.get().getPayrollType() == null
                            ? "기존" : conflict.get().getPayrollType().name();
                    skipReason = String.format("같은 날짜 %s 명세서 존재", existingType);
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
        BonusBatchPreviewResDto previewRes = preview(companyId, req);
        PayrollType payrollType = resolvePayrollType(req.getBonusKind());
        String itemName = resolveItemName(req.getBonusKind());

        List<UUID> created = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (BonusBatchPreviewResDto.TargetEntry t : previewRes.getTargets()) {
            if (t.getSkipReason() != null) continue;
            if (t.getBonusAmount() <= 0) continue;
            try {
                // DB unique key (companyId, memberId, payrollYearMonthDay) - PayrollType 무관 충돌 방지
                // 같은 직원 + 같은 날짜에 어떤 PayrollType 이든 명세서 있으면 skip
                Optional<Payroll> existing = payrollRepository
                        .findByCompanyIdAndMemberIdAndPayrollYearMonthDay(
                                companyId, t.getMemberId(), req.getPayDate());
                if (existing.isPresent()) {
                    String existingType = existing.get().getPayrollType() == null
                            ? "기존" : existing.get().getPayrollType().name();
                    failures.add(String.format("%s: 같은 날짜에 %s 명세서 이미 존재 - 다른 지급일로 발행하세요",
                            t.getName(), existingType));
                    continue;
                }

                Salary salary = salaryRepository.findActiveSalary(
                                t.getMemberId(), companyId, req.getPayDate())
                        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "활성 급여 정보 없음"));

                String targetYearMonth = req.getPayDate().getYear() + "-"
                        + String.format("%02d", req.getPayDate().getMonthValue());
                Payroll payroll = Payroll.builder()
                        .companyId(companyId)
                        .memberId(t.getMemberId())
                        .salaryId(salary.getSalaryId())
                        .payrollYearMonthDay(req.getPayDate())
                        .targetYearMonth(targetYearMonth)
                        .payrollStatus(PayrollStatus.DRAFT)
                        .payrollType(payrollType)
                        .totalPayment(t.getBonusAmount())
                        .totalDeduction(0L)
                        .netPay(t.getBonusAmount())
                        .delYn("N")
                        .build();
                Payroll saved = payrollRepository.save(payroll);

                PayrollItem item = PayrollItem.builder()
                        .payroll(saved)
                        .itemName(itemName)
                        .itemType(ItemType.EARNING)
                        .amount(t.getBonusAmount())
                        .displayOrder(10)
                        .isTaxableYn("Y")
                        .delYn("N")
                        .build();
                payrollItemRepository.save(item);

                created.add(saved.getPayrollId());
            } catch (Exception e) {
                String msg = String.format("%s: %s", t.getName(), e.getMessage());
                failures.add(msg);
                log.warn("[BONUS-BATCH] 발행 실패 - {}", msg);
            }
        }
        log.info("[BONUS-BATCH] 일괄 발행 완료 companyId={} kind={} payDate={} created={} failed={}",
                companyId, req.getBonusKind(), req.getPayDate(), created.size(), failures.size());
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
        switch (req.getBonusKind()) {
            case REGULAR -> {
                if (!"Y".equals(policy.getUseRegularBonusYn())) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "정기상여가 비활성화된 정책입니다.");
                }
                if (req.getRatePercent() == null) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "정기상여 지급 비율(%)을 입력하세요.");
                }
            }
            case PERFORMANCE -> {
                if (!"Y".equals(policy.getUsePerformanceBonusYn())) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "성과급이 비활성화된 정책입니다.");
                }
                if (req.getRatePercent() == null) {
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

    private String checkEligibility(BonusPolicy policy, BonusBatchReqDto req, MemberResDto m) {
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
        return null;
    }

    private long computeBonusAmount(BonusBatchReqDto req, BonusPolicy policy, long baseSalary) {
        return switch (req.getBonusKind()) {
            case REGULAR, PERFORMANCE -> {
                BigDecimal rate = req.getRatePercent();
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
                    BigDecimal rate = policy.getHolidayBonusValue();
                    if (rate == null) yield 0L;
                    yield BigDecimal.valueOf(baseSalary)
                            .multiply(rate)
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
