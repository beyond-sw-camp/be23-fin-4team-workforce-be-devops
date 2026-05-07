package com._team._team.attendance.domain;

import com._team._team.attendance.domain.enums.BalanceType;
import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * 회사별 휴가 종류 정의
 * 법정 기본 8종은 회사 생성 시 자동 시드, 커스텀 휴가 추가 허용
 */
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Builder
@Getter
@Table(
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_company_leave_type_code",
                        columnNames = {"companyId", "code"})
        },
        indexes = {
                @Index(name = "idx_company_leave_type_company",
                        columnList = "companyId, delYn")
        }
)
public class CompanyLeaveType extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID companyLeaveTypeId;

    @Column(nullable = false)
    private UUID companyId;

    // 회사 내 고유 코드 (ANNUAL, HALF_AM, 또는 커스텀 "REFRESH" 등)
    @Column(nullable = false, length = 50)
    private String code;

    // 표시명 (연차, 반차(오전), 리프레시 휴가 등)
    @Column(nullable = false, length = 100)
    private String name;

    // 차감 대상 balance, null 이면 차감 안 함 (경조, 예비군 등)
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BalanceType balanceType;

    // 1회 사용 시 차감 일수 (연차=1.0, 반차=0.5)
    @Column(nullable = false)
    private Double daysPerUse;

    // 시스템 기본 제공 여부, true 면 삭제 불가
    @Column(nullable = false)
    @Builder.Default
    private Boolean isSystemDefault = false;

    // 유급 여부
    @Column(nullable = false, length = 1)
    @Builder.Default
    private String isPaidYn = "Y";

    // 연간 최대 사용 일수, null 이면 제한 없음
    @Column
    private Double maxDaysPerYear;

    // 증빙 서류 필수 여부 (병가, 경조 등)
    @Column(nullable = false, length = 1)
    @Builder.Default
    private String requireEvidenceYn = "N";

    // 표시 순서
    @Column(nullable = false)
    private Integer displayOrder;

    @Column(nullable = false, length = 1)
    @Builder.Default
    private String delYn = "N";

    /**
     * 시스템 기본 휴가 수정 (연차, 반차는 수정 불가)
     * 나머지 기본 휴가는 일수/유급/증빙도 수정 가능.
     */
    public void update(String name, String isPaidYn, Double maxDaysPerYear,
                       String requireEvidenceYn, Integer displayOrder) {
        this.name = name;
        this.isPaidYn = isPaidYn;
        this.maxDaysPerYear = maxDaysPerYear;
        this.requireEvidenceYn = requireEvidenceYn;
        this.displayOrder = displayOrder;
    }

    /**
     * 커스텀 항목 전체 수정
     */
    public void updateCustom(String name, BalanceType balanceType,
                             Double daysPerUse, String isPaidYn,
                             Double maxDaysPerYear, String requireEvidenceYn,
                             Integer displayOrder) {
        this.name = name;
        this.balanceType = balanceType;
        this.daysPerUse = daysPerUse;
        this.isPaidYn = isPaidYn;
        this.maxDaysPerYear = maxDaysPerYear;
        this.requireEvidenceYn = requireEvidenceYn;
        this.displayOrder = displayOrder;
    }

    public void softDelete() {
        this.delYn = "Y";
    }

    /**
     * 소프트 삭제된 동일 코드 행을 시스템 기본값으로 되살림
     */
    public void restoreSystemDefault(String name, BalanceType balanceType, Double daysPerUse,
                                     String isPaidYn, Double maxDaysPerYear, String requireEvidenceYn,
                                     Integer displayOrder) {
        this.delYn = "N";
        this.name = name;
        this.balanceType = balanceType;
        this.daysPerUse = daysPerUse;
        this.isSystemDefault = true;
        this.isPaidYn = isPaidYn;
        this.maxDaysPerYear = maxDaysPerYear;
        this.requireEvidenceYn = requireEvidenceYn;
        this.displayOrder = displayOrder;
    }

    /**
     * 차감 대상 여부, 급여/잔고 처리 분기용
     */
    public boolean deductsBalance() {
        return this.balanceType != null;
    }
}