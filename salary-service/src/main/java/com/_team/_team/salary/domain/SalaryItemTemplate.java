package com._team._team.salary.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.salary.domain.enums.ItemType;
import com._team._team.salary.domain.enums.TaxCategory;
import com._team._team.salary.dto.reqdto.SalaryItemTemplateUpdateReqDto;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * 급여 항목 템플릿 엔티티
 * - 회사별로 사용할 급여 구성 항목을 정의하는 마스터 테이블 (기본급, 식대, 소득세 등)
 * - 지급(EARNING) / 공제(DEDUCTION)로 구분
 * - 실제 정산 시 PayrollItem에서 이 템플릿을 참조하여 금액을 계산
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryItemTemplate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID salaryItemTemplateId;

    /** 소속 회사 ID */
    @Column(nullable = false)
    private UUID companyId;

    /** 항목명 (예: 기본급, 식대, 교통비, 소득세, 국민연금) */
    @Column(nullable = false)
    private String itemName;

    /** 항목 유형 - EARNING(지급) / DEDUCTION(공제) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemType itemType;

    // 세법 카테고리, 카탈로그 기반이면 자동 복사, 커스텀이면 관리자가 선택
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaxCategory taxCategory;

    // 시스템 기본 항목 여부
    @Column(nullable = false)
    @Builder.Default
    private Boolean isSystemDefault = false;

    /** 과세 여부 - Y이면 소득세 계산에 포함 */
    @Column(nullable = false, length = 1)
    private String isTaxableYn;

    // 통상임금 포함 여부 Y면 시급 환산 기준에 포함
    // 정기·일률·고정 지급 항목만 Y (직책수당 자격수당 등)
    // 변동성 수당 (성과급 비정기상여) 및 비과세 실비 (식대 자가운전) 는 N
    @Column(nullable = false, length = 1)
    @Builder.Default
    private String isOrdinaryWageYn = "N";

    /** 표시 순서 */
    @Column(nullable = false)
    private Integer displayOrder;

    /**
     * 회사가 정한 기본 지급 금액
     */
    @Column
    private Long defaultAmount;

    /**
     * 회사 공통 적용 여부
     * - "Y" = 회사 공통: 모든 활성 직원 수당
     * - "N" = 개인 차등: (직책수당, 자격수당 등)
     * 기본값 "N"
     */
    @Column(nullable = false, length = 1)
    @Builder.Default
    private String applyToAllYn = "N";

    /** 삭제 여부 */
    @Column(length = 1)
    @Builder.Default
    private String delYn = "N";

    /**
     * 급여 항목 템플릿 정보 수정
     */
    public void update(SalaryItemTemplateUpdateReqDto reqDto) {
        this.itemName = reqDto.getItemName();
        this.itemType = reqDto.getItemType();
        this.isTaxableYn = reqDto.getIsTaxableYn();
        if (reqDto.getIsOrdinaryWageYn() != null) {
            this.isOrdinaryWageYn = reqDto.getIsOrdinaryWageYn();
        }
        this.displayOrder = reqDto.getDisplayOrder();
        // defaultAmount 는 명시적으로 전달되면 갱신
        this.defaultAmount = reqDto.getDefaultAmount();
        if (reqDto.getApplyToAllYn() != null) {
            this.applyToAllYn = "Y".equalsIgnoreCase(reqDto.getApplyToAllYn()) ? "Y" : "N";
        }
    }

    /** 소프트 삭제 */
    public void softDelete() {
        this.delYn = "Y";
    }
}
