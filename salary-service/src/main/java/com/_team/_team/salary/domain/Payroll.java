package com._team._team.salary.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.salary.domain.enums.PayrollStatus;
import com._team._team.salary.domain.enums.PayrollType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 급여대장 엔티티 (직원별 월별 급여명세서)
 * - 직원 1명의 월별 급여 정산 내역
 * - Salary(급여 정보)를 참조하여 기본급 기준을 잡음
 * - PayrollItem과 1:N 관계 (지급/공제 항목별 금액)
 * - DRAFT → CONFIRMED → PAID 순서로 상태 전이
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(
        name = "payroll",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payroll_company_member_date",
                        columnNames = {"companyId", "memberId", "payrollYearMonthDay"}
                )
        }
)
public class Payroll extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID payrollId;

    /** 소속 회사 ID */
    @Column(nullable = false)
    private UUID companyId;

    /** 적용 급여 정책 ID */
    @Column(nullable = false)
    private UUID salaryId;

    /** 대상 직원 ID (member-service에서 관리) */
    @Column(nullable = false)
    private UUID memberId;

    /** 급여 지급일  */
    @Column(nullable = false)
    private LocalDate payrollYearMonthDay;

    /**
     * 정산 대상 월 (YYYY-MM)
     * - 어느 월분 급여인지, SalaryPolicy.payCycleType 에 따라 결정
     */
    @Column(length = 7)
    private String targetYearMonth;

    /** 실제 지급일 (지급 완료 시 세팅) */
    private LocalDate paidAt;

    /** 총 지급액 (EARNING 항목 합계) */
    @Column(nullable = false)
    private Long totalPayment;

    /** 총 공제액 (DEDUCTION 항목 합계) */
    @Column(nullable = false)
    private Long totalDeduction;

    /** 실수령액 (totalPayment - totalDeduction) */
    @Column(nullable = false)
    private Long netPay;

    /** 삭제 여부 */
    @Column(nullable = false, length = 1)
    @Builder.Default
    private String delYn = "N";

    /** 급여대장 상태 (DRAFT / CONFIRMED / PAID) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PayrollStatus payrollStatus = PayrollStatus.DRAFT;

    /** 급여 구분 (정기급여 / 성과급 / 특별상여 / 소급분 / 퇴직 정산) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private PayrollType payrollType = PayrollType.REGULAR_MONTHLY;

    /** 급여대장 항목 목록 (1:N) */
    @OneToMany(mappedBy = "payroll", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PayrollItem> payrollItemList = new ArrayList<>();

    /**
     * 급여대장 기본 정보 수정
     * - DRAFT 상태에서만 호출
     */
    public void update(LocalDate payrollYearMonthDay,
                       Long totalPayment, Long totalDeduction, Long netPay){
        this.payrollYearMonthDay = payrollYearMonthDay;
        this.totalPayment = totalPayment;
        this.totalDeduction = totalDeduction;
        this.netPay = netPay;
    }

    /** 금액 재계산 (항목 추가/수정/삭제 후 호출) */
    public void recalculate(Long totalPayment, Long totalDeduction){
        this.totalPayment = totalPayment;
        this.totalDeduction = totalDeduction;
        this.netPay = totalPayment - totalDeduction;
    }

     /** 급여 확정 (DRAFT → CONFIRMED) */
    public void confirm() {
        this.payrollStatus = PayrollStatus.CONFIRMED;
    }

    /** 지급 완료 (CONFIRMED → PAID) */
    public void pay() {
        this.payrollStatus = PayrollStatus.PAID;
        this.paidAt = LocalDate.now();
    }

    /** 시드 전용 - 실제 지급일로 paidAt 보정 */
    public void overridePaidAt(LocalDate actualPaidAt) {
        this.paidAt = actualPaidAt;
    }

    /** 소프트 삭제 */
    public void delete() {
        this.delYn = "Y";
    }

    /** 수정 가능 여부 확인 */
    public boolean isModifiable() {
        return this.payrollStatus == PayrollStatus.DRAFT;
    }
}
