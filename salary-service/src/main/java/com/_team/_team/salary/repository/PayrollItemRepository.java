package com._team._team.salary.repository;

import com._team._team.salary.domain.PayrollItem;
import com._team._team.salary.domain.enums.ItemType;
import com._team._team.salary.domain.enums.PayrollType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface PayrollItemRepository extends JpaRepository<PayrollItem, UUID> {

    /** 급여대장별 항목 목록 (삭제 안 된 것만, 표시 순서대로) */
    List<PayrollItem> findByPayroll_PayrollIdAndDelYnOrderByDisplayOrder(UUID payrollId, String delYn);

    /**
     * 급여대장의 항목 유형별 금액 합계 (DB 레벨 SUM)
     * - Java에서 전체 항목을 가져와서 stream 합산하는 것보다 효율적
     * - 항목이 없으면 COALESCE로 0 반환
     */
    /** 같은 급여대장 내 동일 항목명 존재 여부 확인 (중복 방지) */
    boolean existsByPayroll_PayrollIdAndItemNameAndDelYn(UUID payrollId, String itemName, String delYn);

    @Query("SELECT COALESCE(SUM(pi.amount), 0) FROM PayrollItem pi " +
           "WHERE pi.payroll.payrollId = :payrollId " +
           "AND pi.delYn = 'N' " +
           "AND pi.itemType = :itemType")
    Long sumAmountByPayrollIdAndItemType(@Param("payrollId") UUID payrollId,
                                         @Param("itemType") ItemType itemType);

    /**
     * 회사 + 월 범위 + 항목명 IN 조건으로 GROUP BY itemName SUM amount
     * 4대보험 + 원천세 집계 한 쿼리
     */
    @Query("""
        SELECT pi.itemName, COALESCE(SUM(pi.amount), 0)
        FROM PayrollItem pi
        WHERE pi.payroll.companyId = :companyId
          AND pi.payroll.payrollYearMonthDay BETWEEN :from AND :to
          AND pi.payroll.delYn = 'N'
          AND pi.delYn = 'N'
          AND pi.itemName IN :itemNames
        GROUP BY pi.itemName
        """)
    List<Object[]> sumByItemNamesInMonth(@Param("companyId") UUID companyId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to,
                                         @Param("itemNames") Collection<String> itemNames);

    /**
     * 회사 + 월 범위 안 PayrollItem 이 속한 직원 수 카운트
     */
    @Query("""
        SELECT COUNT(DISTINCT pi.payroll.memberId)
        FROM PayrollItem pi
        WHERE pi.payroll.companyId = :companyId
          AND pi.payroll.payrollYearMonthDay BETWEEN :from AND :to
          AND pi.payroll.delYn = 'N'
          AND pi.delYn = 'N'
        """)
    long countMembersInMonth(@Param("companyId") UUID companyId,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to);

    /**
     * 회사 + 월 범위 안 정기급여중 수당 조회
     * 기본급 / 정기상여 / 명절상여 / 성과급 / 퇴직 관련 / 미사용 연차 수당 제외
     */
    @Query("""
        SELECT pi
        FROM PayrollItem pi
        WHERE pi.payroll.companyId = :companyId
          AND pi.payroll.payrollYearMonthDay BETWEEN :from AND :to
          AND pi.payroll.delYn = 'N'
          AND pi.delYn = 'N'
          AND pi.itemType = :earningType
          AND pi.payroll.payrollType = :regularType
          AND pi.itemName NOT IN :excludeNames
        """)
    List<PayrollItem> findAllowanceLinesInMonth(@Param("companyId") UUID companyId,
                                                @Param("from") LocalDate from,
                                                @Param("to") LocalDate to,
                                                @Param("earningType") ItemType earningType,
                                                @Param("regularType") PayrollType regularType,
                                                @Param("excludeNames") Collection<String> excludeNames);
}
