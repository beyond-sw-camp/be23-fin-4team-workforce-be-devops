package com._team._team.salary.service;

import com._team._team.dto.BusinessException;
import com._team._team.salary.domain.BonusPolicy;
import com._team._team.salary.domain.enums.BonusEligibilityScope;
import com._team._team.salary.dto.reqdto.BonusPolicyCreateReqDto;
import com._team._team.salary.dto.reqdto.BonusPolicyUpdateReqDto;
import com._team._team.salary.dto.resdto.BonusPolicyResDto;
import com._team._team.salary.repository.BonusPolicyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 회사별 보너스 (정기상여 / 성과급 / 명절상여) 정책 관리
@Slf4j
@Service
@Transactional
public class BonusPolicyService {

    private final BonusPolicyRepository bonusPolicyRepository;

    @Autowired
    public BonusPolicyService(BonusPolicyRepository bonusPolicyRepository) {
        this.bonusPolicyRepository = bonusPolicyRepository;
    }

    // 정책 등록 이전 활성 정책 자동 마감
    public BonusPolicyResDto create(UUID companyId, BonusPolicyCreateReqDto reqDto) {
        validatePeriod(reqDto.getEffectiveFrom(), reqDto.getEffectiveTo());
        validateBonusFields(reqDto);

        // uk(companyId, effectiveFrom) 가 delYn 을 보지 않아 소프트 삭제만 하면 같은 시작일 재등록이 막힘
        Optional<BonusPolicy> sameKey =
                bonusPolicyRepository.findByCompanyIdAndEffectiveFrom(companyId, reqDto.getEffectiveFrom());
        if (sameKey.isPresent()) {
            BonusPolicy row = sameKey.get();
            if ("Y".equals(row.getDelYn())) {
                bonusPolicyRepository.delete(row);
                bonusPolicyRepository.flush();
                log.info("[BONUS-POLICY] 삭제된 동일 적용시작일 행 물리 제거 후 재등록 허용 companyId={} policyId={} effectiveFrom={}",
                        companyId, row.getBonusPolicyId(), reqDto.getEffectiveFrom());
            } else {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "동일 적용 시작일의 보너스 정책이 이미 있습니다. 기존 정책을 수정하거나 적용 시작일을 변경해 주세요.");
            }
        }

        bonusPolicyRepository.findActiveAt(companyId, reqDto.getEffectiveFrom())
                .ifPresent(prev -> {
                    LocalDate closingDate = reqDto.getEffectiveFrom().minusDays(1);
                    if (!prev.getEffectiveFrom().isAfter(closingDate)) {
                        prev.closeAt(closingDate);
                        log.info("[BONUS-POLICY] 이전 활성 정책 자동 마감 companyId={} prevId={} closeAt={}",
                                companyId, prev.getBonusPolicyId(), closingDate);
                    }
                });

        BonusPolicy saved = bonusPolicyRepository.save(BonusPolicy.builder()
                .companyId(companyId)
                .useRegularBonusYn(normalizeYn(reqDto.getUseRegularBonusYn()))
                .regularBonusAnnualRate(reqDto.getRegularBonusAnnualRate())
                .regularBonusPaymentCount(reqDto.getRegularBonusPaymentCount())
                .usePerformanceBonusYn(normalizeYn(reqDto.getUsePerformanceBonusYn()))
                .performanceBonusMaxRate(reqDto.getPerformanceBonusMaxRate())
                .performanceBonusBasis(reqDto.getPerformanceBonusBasis())
                .useHolidayBonusYn(normalizeYn(reqDto.getUseHolidayBonusYn()))
                .holidayBonusType(reqDto.getHolidayBonusType())
                .holidayBonusValue(reqDto.getHolidayBonusValue())
                .eligibilityScope(reqDto.getEligibilityScope())
                .minTenureMonths(reqDto.getMinTenureMonths() != null ? reqDto.getMinTenureMonths() : 0)
                .excludeOnLeaveYn(normalizeYn(reqDto.getExcludeOnLeaveYn() != null ? reqDto.getExcludeOnLeaveYn() : "Y"))
                .effectiveFrom(reqDto.getEffectiveFrom())
                .effectiveTo(reqDto.getEffectiveTo())
                .memo(reqDto.getMemo())
                .build());

        log.info("[BONUS-POLICY] 등록 companyId={} policyId={} regular={} perf={} holiday={}",
                companyId, saved.getBonusPolicyId(),
                saved.getUseRegularBonusYn(), saved.getUsePerformanceBonusYn(), saved.getUseHolidayBonusYn());
        return BonusPolicyResDto.fromEntity(saved);
    }

    // 정책 수정 시작일 변경 불가 나머지 항목 일괄 갱신
    public BonusPolicyResDto update(UUID companyId, UUID policyId,
                                    BonusPolicyUpdateReqDto reqDto) {
        BonusPolicy policy = findOrThrow(policyId);
        validateCompany(policy, companyId);
        validatePeriod(policy.getEffectiveFrom(), reqDto.getEffectiveTo());
        validateBonusFieldsForUpdate(reqDto);

        policy.update(
                normalizeYn(reqDto.getUseRegularBonusYn()),
                reqDto.getRegularBonusAnnualRate(),
                reqDto.getRegularBonusPaymentCount(),
                normalizeYn(reqDto.getUsePerformanceBonusYn()),
                reqDto.getPerformanceBonusMaxRate(),
                reqDto.getPerformanceBonusBasis(),
                normalizeYn(reqDto.getUseHolidayBonusYn()),
                reqDto.getHolidayBonusType(),
                reqDto.getHolidayBonusValue(),
                reqDto.getEligibilityScope(),
                reqDto.getMinTenureMonths(),
                normalizeYn(reqDto.getExcludeOnLeaveYn() != null ? reqDto.getExcludeOnLeaveYn() : "Y"),
                reqDto.getEffectiveTo(),
                reqDto.getMemo()
        );
        log.info("[BONUS-POLICY] 수정 companyId={} policyId={}", companyId, policyId);
        return BonusPolicyResDto.fromEntity(policy);
    }

    // 정책 소프트 삭제
    public void delete(UUID companyId, UUID policyId) {
        BonusPolicy policy = findOrThrow(policyId);
        validateCompany(policy, companyId);
        policy.softDelete();
        log.info("[BONUS-POLICY] 삭제 companyId={} policyId={}", companyId, policyId);
    }

    // 회사 전체 정책 이력 조회
    @Transactional(readOnly = true)
    public List<BonusPolicyResDto> listByCompany(UUID companyId) {
        return bonusPolicyRepository
                .findByCompanyIdAndDelYnOrderByEffectiveFromDesc(companyId, "N")
                .stream()
                .map(BonusPolicyResDto::fromEntity)
                .toList();
    }

    // 활성 정책 조회 없으면 기본 정책 자동 생성 모두 비활성 상태
    public BonusPolicyResDto getOrCreateActive(UUID companyId) {
        return bonusPolicyRepository.findActiveAt(companyId, LocalDate.now())
                .map(BonusPolicyResDto::fromEntity)
                .orElseGet(() -> {
                    log.warn("[BONUS-POLICY] 활성 정책 없음 기본 비활성 정책 자동 생성 companyId={}",
                            companyId);
                    BonusPolicy created = bonusPolicyRepository.save(
                            BonusPolicy.builder()
                                    .companyId(companyId)
                                    .useRegularBonusYn("N")
                                    .usePerformanceBonusYn("N")
                                    .useHolidayBonusYn("N")
                                    .eligibilityScope(BonusEligibilityScope.ALL)
                                    .effectiveFrom(LocalDate.now())
                                    .memo("활성 정책 없음 자동 생성 모두 비활성")
                                    .build());
                    return BonusPolicyResDto.fromEntity(created);
                });
    }

    /* ───── 검증 ───── */

    // 기간 정합성 검증
    private void validatePeriod(LocalDate from, LocalDate to) {
        if (from == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "적용 시작일은 필수입니다");
        }
        if (to != null && to.isBefore(from)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "적용 종료일은 시작일 이후여야 합니다");
        }
    }

    // 사용 여부에 따른 필수 필드 검증
    private void validateBonusFields(BonusPolicyCreateReqDto dto) {
        if ("Y".equals(dto.getUseRegularBonusYn())) {
            if (dto.getRegularBonusAnnualRate() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "정기상여 사용 시 연 누계 비율은 필수입니다");
            }
            if (dto.getRegularBonusPaymentCount() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "정기상여 사용 시 연 지급 횟수는 필수입니다");
            }
        }
        if ("Y".equals(dto.getUsePerformanceBonusYn()) && dto.getPerformanceBonusMaxRate() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "성과급 사용 시 최대 지급 비율은 필수입니다");
        }
        if ("Y".equals(dto.getUseHolidayBonusYn())) {
            if (dto.getHolidayBonusType() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "명절상여 사용 시 지급 방식은 필수입니다");
            }
            if (dto.getHolidayBonusValue() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "명절상여 사용 시 지급 값은 필수입니다");
            }
        }
    }

    private void validateBonusFieldsForUpdate(BonusPolicyUpdateReqDto dto) {
        if ("Y".equals(dto.getUseRegularBonusYn())) {
            if (dto.getRegularBonusAnnualRate() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "정기상여 사용 시 연 누계 비율은 필수입니다");
            }
            if (dto.getRegularBonusPaymentCount() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "정기상여 사용 시 연 지급 횟수는 필수입니다");
            }
        }
        if ("Y".equals(dto.getUsePerformanceBonusYn()) && dto.getPerformanceBonusMaxRate() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "성과급 사용 시 최대 지급 비율은 필수입니다");
        }
        if ("Y".equals(dto.getUseHolidayBonusYn())) {
            if (dto.getHolidayBonusType() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "명절상여 사용 시 지급 방식은 필수입니다");
            }
            if (dto.getHolidayBonusValue() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "명절상여 사용 시 지급 값은 필수입니다");
            }
        }
    }

    private String normalizeYn(String yn) {
        return "Y".equalsIgnoreCase(yn) ? "Y" : "N";
    }

    private BonusPolicy findOrThrow(UUID policyId) {
        return bonusPolicyRepository.findById(policyId)
                .filter(p -> "N".equals(p.getDelYn()))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "보너스 정책을 찾을 수 없습니다"));
    }

    private void validateCompany(BonusPolicy policy, UUID companyId) {
        if (!policy.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "다른 회사의 정책에 접근할 수 없습니다");
        }
    }
}
