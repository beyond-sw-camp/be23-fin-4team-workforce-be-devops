package com._team._team.salary.domain;


import com._team._team.domain.BaseTimeEntity;
import com._team._team.salary.domain.enums.RetirementType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// 회사별 퇴직급여 제도 정책
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Builder
@Getter
@Table(
        indexes = {
                @Index(name = "idx_retirement_policy_company_active",
                        columnList = "companyId, effectiveFrom")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_retirement_policy_company_effective_from",
                        columnNames = {"companyId", "effectiveFrom"}
                )
        }
)
public class RetirementPolicy extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID retirementPolicyId;

    @Column(nullable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RetirementType retirementType;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    // 정책 등록 시 비고 메모 변경 사유 등
    @Column(length = 500)
    private String memo;

    // DC 형 월 부담금 비율(%) - 기본 8.33 (1/12), 회사 추가 적립 시 더 높게 설정
    // LEGAL/DB 형 정책에서는 NULL
    @Column(precision = 5, scale = 2)
    private BigDecimal dcContributionRate;

    // 운용 금융기관명 - DB/DC 형 표시용
    @Column(length = 100)
    private String providerName;

    // 운용 계약/계좌번호 - DB/DC 형 표시용
    @Column(length = 100)
    private String contractNumber;

    // 중간정산 허용 여부 - LEGAL 형에서만 의미, Y/N
    @Column(length = 1)
    private String allowEarlySettlementYn;

    @Column(nullable = false, length = 1)
    @Builder.Default
    private String delYn = "N";

    // 정책 마감 새 정책 등록 시 이전 활성 행 자동 종료용
    public void closeAt(LocalDate closingDate) {
        this.effectiveTo = closingDate;
    }

    // 정책 수정 effectiveFrom 은 변경 불가, 나머지는 가능
    public void update(RetirementType retirementType,
                       LocalDate effectiveTo,
                       String memo,
                       BigDecimal dcContributionRate,
                       String providerName,
                       String contractNumber,
                       String allowEarlySettlementYn) {
        this.retirementType = retirementType;
        this.effectiveTo = effectiveTo;
        this.memo = memo;
        this.dcContributionRate = dcContributionRate;
        this.providerName = providerName;
        this.contractNumber = contractNumber;
        this.allowEarlySettlementYn = allowEarlySettlementYn;
    }

    public void softDelete() {
        this.delYn = "Y";
    }

    // 특정 날짜 시점 활성 여부 판단
    public boolean isActiveAt(LocalDate date) {
        if ("Y".equals(delYn)) return false;
        if (effectiveFrom.isAfter(date)) return false;
        return effectiveTo == null || !effectiveTo.isBefore(date);
    }
}
