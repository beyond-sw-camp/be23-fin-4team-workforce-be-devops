package com._team._team.salary.domain;


import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * 간이세액표, 국세청 고시 기반 소득세 조회용
 * 월 급여 구간 + 부양가족수 -> 월 원천징수액 매핑
 */
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Builder
@Getter
@Table(
        indexes = {
                // 급여 계산 조회
                @Index(name = "idx_tax_lookup",
                        columnList = "effectiveYear, salaryLowerBound, salaryUpperBound")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tax_year_range_dependents",
                        columnNames = {"effectiveYear", "salaryLowerBound", "dependentCount"}
                )
        }
)
public class SimplifiedTaxTable extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID simplifiedTaxTableId;

    // 고시 적용 연도, 예 2026
    @Column(nullable = false)
    private Integer effectiveYear;

    // 월 급여 구간 하한, 포함
    @Column(nullable = false)
    private Long salaryLowerBound;

    // 월 급여 구간 상한, 미만
    @Column(nullable = false)
    private Long salaryUpperBound;

    // 부양가족수, 0~11
    @Column(nullable = false)
    private Integer dependentCount;

    // 월 원천징수세액 (소득세)
    @Column(nullable = false)
    private Long monthlyTaxAmount;

    @Column(nullable = false, length = 1)
    @Builder.Default
    private String delYn = "N";

    public void softDelete() {
        this.delYn = "Y";
    }
}