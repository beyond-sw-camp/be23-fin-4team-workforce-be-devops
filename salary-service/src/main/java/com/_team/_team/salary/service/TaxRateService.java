package com._team._team.salary.service;

import com._team._team.dto.BusinessException;
import com._team._team.salary.repository.TaxRateRepository;
import com._team._team.salary.domain.TaxRate;
import com._team._team.salary.domain.enums.TaxType;
import com._team._team.salary.dto.reqdto.TaxRateCreateReqDto;
import com._team._team.salary.dto.reqdto.TaxRateUpdateReqDto;
import com._team._team.salary.dto.resdto.TaxRateResDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 세율 관리 서비스
 * - 연도별 4대보험 및 소득세 세율 CRUD
 * - 동일 연도/유형 중복 등록 방지
 */
@Slf4j
@Service
@Transactional
public class TaxRateService {

    private final TaxRateRepository taxRateRepository;

    @Autowired
    public TaxRateService(TaxRateRepository taxRateRepository) {
        this.taxRateRepository = taxRateRepository;
    }

    // 세율 생성
    public TaxRateResDto save(TaxRateCreateReqDto reqDto){
        if(taxRateRepository.existsByApplyYearAndTaxType(reqDto.getApplyYear(), reqDto.getTaxType())){
            throw new BusinessException(HttpStatus.CONFLICT, "해당 연도 동일한 세금 유형의 세율이 존재합니다.");
        }
        validateIncomeCap(reqDto.getTaxType(), reqDto.getIncomeCeiling(), reqDto.getIncomeFloor());

        TaxRate taxRate = reqDto.toEntity();
        TaxRate saved = taxRateRepository.save(taxRate);
        return TaxRateResDto.fromEntity(saved);
    }

    // 세율 단건 조회
    @Transactional(readOnly = true)
    public TaxRateResDto findById(UUID taxRateId){
        TaxRate taxRate = taxRateRepository.findById(taxRateId)
                .orElseThrow(()-> new BusinessException(HttpStatus.NOT_FOUND,"세율 정보를 찾을 수 없습니다."));
        return TaxRateResDto.fromEntity(taxRate);
    }

    // 연도별 세율 목록 조회
    @Transactional(readOnly = true)
    public List<TaxRateResDto> findByApplyYear(Integer applyYear){
        return taxRateRepository.findByApplyYear(applyYear).stream()
                .map(TaxRateResDto::fromEntity).toList();
    }

    // 세율 수정
    public TaxRateResDto update(UUID taxRateId, TaxRateUpdateReqDto reqDto){
        TaxRate taxRate = taxRateRepository.findById(taxRateId)
                .orElseThrow(()->new BusinessException(HttpStatus.NOT_FOUND,"세율 정보를 찾을 수 없습니다."));
        validateIncomeCap(reqDto.getTaxType(), reqDto.getIncomeCeiling(), reqDto.getIncomeFloor());
        taxRate.update(reqDto);
        return TaxRateResDto.fromEntity(taxRate);
    }

    // 세율 삭제
    public void delete(UUID taxRateId){
        TaxRate taxRate = taxRateRepository.findById(taxRateId)
                .orElseThrow(()-> new BusinessException(HttpStatus.NOT_FOUND, "세율 정보를 찾을 수 없습니다."));
        taxRateRepository.delete(taxRate);
    }

    /**
     * 지정 연도의 표준 4대보험 + 세금 세율을 시드, 이미 있는 유형은 건너뜀 (멱등)
     * 실제 요율은 매년 법령 개정에 따라 달라지므로 등록 후 관리자가 검증/수정 필요
     *
     * 2026년 기준 표준 요율
     * - 국민연금     근로자 4.5%, 회사 4.5%
     * - 건강보험     근로자 3.545%, 회사 3.545%
     * - 장기요양     건강보험료의 12.95% (근로자/회사 각 6.475% = 0.0033 근사치)
     * - 고용보험     근로자 0.9%, 회사 1.15% (실업급여, 업종/규모 무관)
     * - 산재보험     전액 회사 부담, 평균 1.47% (업종별 상이)
     * - 소득세       간이세액표가 원칙이나 MVP 플레이스홀더 3.0% 세팅
     * - 지방소득세   소득세의 10%
     */
    public SeedResult initializeDefaults(Integer applyYear) {
        List<TaxType> inserted = new ArrayList<>();
        List<TaxType> skipped = new ArrayList<>();

        List<TaxRate> defaults = List.of(
                build(TaxType.NATIONAL_PENSION,      applyYear, "0.0450", "0.0450", 6_170_000L, 390_000L),     // 기준소득월액 상한
                build(TaxType.HEALTH_INSURANCE,      applyYear, "0.0355", "0.0355", 119_625_106L, 280_000L),   // 보수월액 상한
                build(TaxType.LONG_TERM_CARE,        applyYear, "0.1295", "0.1295", null, null),            // 건보료 기반이라 상한 없음
                build(TaxType.EMPLOYMENT_INSURANCE,  applyYear, "0.0090", "0.0115", null, null),            // 고용보험 상한 없음
                build(TaxType.ACCIDENT_INSURANCE,    applyYear, "0.0000", "0.0147", null,       null),
                build(TaxType.INCOME_TAX,            applyYear, "0.0300", null,     null,       null),
                build(TaxType.LOCAL_INCOME_TAX,      applyYear, "0.1000", null,     null,       null)
        );

        for (TaxRate seed : defaults) {
            if (taxRateRepository.existsByApplyYearAndTaxType(applyYear, seed.getTaxType())) {
                skipped.add(seed.getTaxType());
                continue;
            }
            taxRateRepository.save(seed);
            inserted.add(seed.getTaxType());
        }

        log.info("[TaxRate] 시드 applyYear={} inserted={} skipped={}", applyYear, inserted, skipped);
        return new SeedResult(applyYear, inserted.size(), skipped.size());
    }

    private TaxRate build(TaxType taxType, Integer year, String rate, String employerRate,
                          Long ceiling, Long floor) {
        return TaxRate.builder()
                .taxType(taxType)
                .applyYear(year)
                .rate(new BigDecimal(rate))
                .employerRate(employerRate == null ? null : new BigDecimal(employerRate))
                .incomeCeiling(ceiling)
                .incomeFloor(floor)
                .build();
    }

    public record SeedResult(Integer applyYear, int inserted, int skipped) {}

    // 상,하한 지원 여부 + 논리 검증
    private void validateIncomeCap(TaxType taxType, Long ceiling, Long floor) {
        if ((ceiling != null || floor != null) && !taxType.supportsIncomeCap()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    taxType.name() + "은(는) 기준소득 상/하한을 설정할 수 없습니다. "
                            + "국민연금, 건강보험만 지원합니다.");
        }
        if (ceiling != null && floor != null && floor > ceiling) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "하한이 상한보다 클 수 없습니다.");
        }
    }
}
