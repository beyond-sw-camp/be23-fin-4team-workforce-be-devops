package com._team._team.personnel.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.personnel.domain.enums.PersonnelOrderType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 인사발령 이력
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Entity
@Table(
        name = "personnel_order",
        indexes = {
                @Index(name = "idx_po_member_effective",
                        columnList = "memberId, effectiveDate"),
                @Index(name = "idx_po_company_effective",
                        columnList = "companyId, effectiveDate")
        }
)
public class PersonnelOrder extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID personnelOrderId;

    @Column(nullable = false)
    private UUID memberId;

    @Column(nullable = false)
    private UUID companyId;

    /** 발령 유형 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PersonnelOrderType orderType;

    /** 효력 시작일 */
    @Column(nullable = false)
    private LocalDate effectiveDate;

    /** 결재 문서 ID - 이력 역추적용 */
    @Column
    private UUID approvalDocumentId;

    /** 부서 이동 - null 이면 변경 없음 */
    private UUID beforeOrganizationId;
    private UUID afterOrganizationId;

    @Column(length = 100)
    private String beforeOrganizationName;
    @Column(length = 100)
    private String afterOrganizationName;

    /** 직급 변경 - null 이면 변경 없음 */
    @Column(length = 50)
    private String beforeJobGradeName;
    @Column(length = 50)
    private String afterJobGradeName;

    /** 직책 변경 - null 이면 변경 없음 */
    @Column(length = 50)
    private String beforeJobTitleName;
    @Column(length = 50)
    private String afterJobTitleName;

    /** 호봉 변경 (호봉제 회사만 의미) - null 이면 변경 없음 */
    private Integer beforeStep;
    private Integer afterStep;

    /** 발령 사유 / 비고 */
    @Column(length = 500)
    private String reason;

    /** 결재 승인자 ID */
    private UUID approverId;

    /**
     * 실제 적용 여부 , 'Y' 즉시 적용, 'N' 미래 발령
     */
    @Column(nullable = false, length = 1)
    @Builder.Default
    private String appliedYn = "N";

    /** 적용 완료 - 배치/즉시 모두 호출 */
    public void markApplied() {
        this.appliedYn = "Y";
    }
}
