package com._team._team.salary.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.salary.domain.enums.ItemType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * 급여대장 항목 엔티티
 * - 급여대장(Payroll) 1건에 속하는 개별 지급/공제 항목
 * - 항목명과 유형은 SalaryItemTemplate에서 스냅샷하여 저장
 * - 예: 기본급 3,000,000 / 소득세 -150,000
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
public class PayrollItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID payrollItemId;

    /** 소속 급여대장 (N:1) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_id", nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Payroll payroll;

    /** 항목명 (예: 기본급, 식대, 소득세) */
    @Column(nullable = false)
    private String itemName;

    /** 항목 유형 - EARNING(지급) / DEDUCTION(공제) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemType itemType;

    /** 금액 */
    @Column(nullable = false)
    private Long amount;

    /** 표시 순서 */
    private Integer displayOrder;

    /** 과세 여부 */
    @Column(nullable = false, length = 1)
    @Builder.Default
    private String isTaxableYn = "Y";

    /** 삭제 여부 */
    @Column(nullable = false, length = 1)
    @Builder.Default
    private String delYn = "N";

    /** 금액 수정 */
    public void updateAmount(Long amount){
        this.amount = amount;
    }

    /** 표시 순서 수정 */
    public void updateDisplayOrder(Integer displayOrder){
        this.displayOrder = displayOrder;
    }

    /** 소프트 삭제 */
    public void delete() {
        this.delYn = "Y";
    }

    /**
     * 템플릿 기반 자동 생성 (급여대장 생성 시 일괄 사용)
     * - createPayroll()에서 회사 템플릿 → PayrollItem 변환용
     * - "기본급"이면 baseSalary, 그 외는 0원으로 호출측에서 amount 결정
     */
    public static PayrollItem fromTemplate(Payroll payroll, SalaryItemTemplate template, long amount) {
        return PayrollItem.builder()
                .payroll(payroll)
                .itemName(template.getItemName())
                .itemType(template.getItemType())
                .amount(amount)
                .displayOrder(template.getDisplayOrder())
                .isTaxableYn(template.getIsTaxableYn())
                .build();
    }
}
