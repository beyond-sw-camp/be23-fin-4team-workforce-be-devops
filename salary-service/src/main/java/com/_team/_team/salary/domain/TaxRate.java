package com._team._team.salary.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.salary.domain.enums.TaxType;
import com._team._team.salary.dto.reqdto.TaxRateUpdateReqDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;


/**
 * 세율 엔티티
 * - 연도별 4대보험 및 소득세 세율 관리
 * - rate: 근로자 부담률
 * - employerRate: 사용자(회사) 부담률
 * - 예: 2026년 국민연금 근로자 4.5%, 회사 4.5%
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
public class TaxRate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID taxRateId;

    /** 세금 유형 (국민연금, 건강보험, 소득세 등) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaxType taxType;

    /** 근로자 부담률 (예: 0.0450 = 4.5%) */
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal rate;

    /**
     * 기준소득 상한 (월, 원). null 이면 상한 없음.
     * - 국민연금: 2026년 617만원
     * - 건강보험: 연 14억 수준 (매년 변동)
     */
    @Column
    private Long incomeCeiling;

    /**
     * 기준소득 하한 (월, 원). null 이면 하한 없음.
     * - 국민연금: 2026년 39만원
     * - 건강보험: 약 28만원 (매년 변동)
     */
    @Column
    private Long incomeFloor;

    /** 적용 연도 (예: 2026) */
    @Column(nullable = false)
    private Integer applyYear;

    /** 사용자(회사) 부담률 (예:0.0450 = 4.5%) */
    @Column(precision = 10, scale = 4)
    private BigDecimal employerRate;

    /**
     * 세율 수정
     * - @Transactional 내에서 더티체킹으로 자동 UPDATE
     */
    public void update(TaxRateUpdateReqDto reqDto){
        this.taxType = reqDto.getTaxType();
        this.rate = reqDto.getRate();
        this.applyYear = reqDto.getApplyYear();
        this.employerRate = reqDto.getEmployerRate();
        this.incomeCeiling = reqDto.getIncomeCeiling();
        this.incomeFloor = reqDto.getIncomeFloor();
    }
}
