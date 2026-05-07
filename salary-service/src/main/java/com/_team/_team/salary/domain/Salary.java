package com._team._team.salary.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.salary.domain.enums.TaxReductionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com._team._team.salary.dto.reqdto.SalaryUpdateReqDto;
import lombok.*;
import jakarta.persistence.*;
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
public class Salary extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID salaryId;

    /** 직원 ID (member-service에서 관리) */
    @Column(nullable = false)
    private UUID memberId;

    /** 소속 회사 ID (Gateway 헤더에서 전달받음) */
    @Column(nullable = false)
    private UUID companyId;

    /** 적용 급여 정책 ID (salary_policy) */
    @Column(nullable = false)
    private UUID salaryPolicyId;

    /**
     * 호봉, (호봉제인 회사용)
     */
    private Integer step;

    /** 기본급 */
    @Column(nullable = false)
    private Long baseSalary;

    /** 직급명 (생성 시점 스냅샷 - 이력 추적용) */
    private String jobGradeName;

    /** 직책명 (생성 시점 스냅샷 - 이력 추적용) */
    private String jobTitleName;

    /** 적용 시작일 */
    @Column(nullable = false)
    private LocalDate effectiveFrom;

    /** 적용 종료일 (null이면 현재 적용 중) */
    private LocalDate effectiveTo;

    /**
     * 부양가족수 간이세액표 룩업 시 사용
     * 기본 1 본인 만
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer dependentCount = 1;

    /**
     * 8세 이상 20세 이하 자녀 수
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer childUnder20Count = 0;

    /**
     * 소득세 감면 유형 - 조세특례제한법 등
     * NONE 이 기본, YOUTH_SME (청년 중소기업) / DISABLED / FOREIGNER / ETC
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TaxReductionType taxReductionType = TaxReductionType.NONE;

    /**
     * 감면율 (0.00 ~ 1.00), 예: 청년 중소기업 5년 첫 90% 감면 → 0.90
     * NONE 이면 0.00 으로 저장
     */
    @Column(precision = 4, scale = 2)
    private BigDecimal taxReductionRate;

    /**
     * 감면 종료일, 청년 SME 감면은 5년 한정 등 기간 제약이 있어 종료일 도래 후엔 감면 미적용
     * null = 무기한 (실제로는 거의 없으니 항상 입력 권장)
     */
    private LocalDate taxReductionEffectiveTo;

    /**
     * 급여 정보 수정
     * 직급명/직책명이 reqDto 에 비어 있으면 기존 값 보존
     * 세무 추가 필드(자녀수·감면)도 reqDto 에 들어오면 갱신, null 이면 기본값 적용
     */
    public void update(SalaryUpdateReqDto reqDto, long resolvedBaseSalary){
        this.salaryPolicyId = reqDto.getSalaryPolicyId();
        this.baseSalary = resolvedBaseSalary;
        this.step = reqDto.getStep();
        if (reqDto.getJobGradeName() != null && !reqDto.getJobGradeName().isBlank()) {
            this.jobGradeName = reqDto.getJobGradeName();
        }
        if (reqDto.getJobTitleName() != null && !reqDto.getJobTitleName().isBlank()) {
            this.jobTitleName = reqDto.getJobTitleName();
        }
        this.effectiveFrom = reqDto.getEffectiveFrom();
        this.effectiveTo = reqDto.getEffectiveTo();
        this.dependentCount = reqDto.getDependentCount() == null ? 1 : reqDto.getDependentCount();
        this.childUnder20Count = reqDto.getChildUnder20Count() == null ? 0 : reqDto.getChildUnder20Count();
        this.taxReductionType = reqDto.getTaxReductionType() == null
                ? TaxReductionType.NONE
                : reqDto.getTaxReductionType();
        this.taxReductionRate = reqDto.getTaxReductionRate();
        this.taxReductionEffectiveTo = reqDto.getTaxReductionEffectiveTo();
    }

    /**
     * 정산 시점에 소득세 감면이 적용되는지 판정.
     * - taxReductionType != NONE
     * - taxReductionRate > 0
     * - taxReductionEffectiveTo == null 이거나 정산일이 effectiveTo 이전·당일
     */
    public boolean isTaxReductionApplicableAt(LocalDate date) {
        if (taxReductionType == null || taxReductionType == TaxReductionType.NONE) return false;
        if (taxReductionRate == null || taxReductionRate.signum() <= 0) return false;
        return taxReductionEffectiveTo == null || !taxReductionEffectiveTo.isBefore(date);
    }

    /**
     * 현재 적용 중인 급여인지 확인
     * - 시작일이 오늘 이전이고, 종료일이 업거나 오늘 이후면 활성 상태
     */
    public boolean isActive(){
        LocalDate today = LocalDate.now();
        boolean started = !effectiveFrom.isAfter(today);
        boolean notEnded = effectiveTo == null || !effectiveTo.isBefore(today);
        return started && notEnded;
    }

    /**
     * 적용 기간 종료 - 새 급여 생성 시 이전 급여를 자동 마감
     * 예: 새 급여 effectiveFrom이 03-01이면, 이전 급여 effectiveTo를 02-28로 설정
     */
    public void closeEffectivePeriod(LocalDate endDate) {
        this.effectiveTo = endDate;
    }
}
