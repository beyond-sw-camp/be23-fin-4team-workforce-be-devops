package com._team._team.salary.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.salary.dto.reqdto.PayGradeTableUpdateReqDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.salary.dto.reqdto.PayGradeTableUpdateReqDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 호봉표 (호봉 -> 기본급)
 * 직급 무관. 직급별 차등은 SalaryItemTemplate 의 직책수당 등으로 처리
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Entity
@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_pay_grade_table_active",
                        columnNames = {"companyId", "step", "effectiveFrom"}
                )
        },
        indexes = {
                @Index(name = "idx_pay_grade_company_step", columnList = "companyId, step")
        }
)
public class PayGradeTable extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID payGradeTableId;

    @Column(nullable = false)
    private UUID companyId;

    /** 호봉, 1~N. 근속 연차 기반으로 사용 */
    @Column(nullable = false)
    private Integer step;

    @Column(nullable = false)
    private Long baseSalary;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    /** null 이면 활성, 값 있으면 해당 일자까지 적용 후 종료 */
    private LocalDate effectiveTo;

    @Column(length = 200)
    private String description;

    @Column(nullable = false, length = 1)
    @Builder.Default
    private String delYn = "N";

    public void update(PayGradeTableUpdateReqDto reqDto) {
        this.baseSalary = reqDto.getBaseSalary();
        this.description = reqDto.getDescription();
    }

    public void closeEffectivePeriod(LocalDate endDate) {
        this.effectiveTo = endDate;
    }

    public void delete() {
        this.delYn = "Y";
    }

    public boolean isActiveOn(LocalDate date) {
        if (!"N".equals(delYn)) return false;
        if (effectiveFrom.isAfter(date)) return false;
        return effectiveTo == null || !effectiveTo.isBefore(date);
    }
}