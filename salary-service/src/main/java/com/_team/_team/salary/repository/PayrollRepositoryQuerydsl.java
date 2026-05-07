package com._team._team.salary.repository;

import com._team._team.salary.domain.Payroll;
import com._team._team.salary.domain.enums.PayrollStatus;
import com._team._team.salary.domain.enums.PayrollType;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Payroll 동적 검색 (QueryDSL)
 * - 관리자 화면, export, 배치 등에서 옵셔널 필터 조합으로 호출
 */
public interface PayrollRepositoryQuerydsl {

    /**
     * 회사 + 월 범위 + 필터로 Payroll 검색
     */
    List<Payroll> searchAdminListInMonth(
            UUID companyId,
            LocalDate from,
            LocalDate to,
            PayrollStatus status,
            PayrollType payrollType,
            Collection<UUID> memberIds);
}
