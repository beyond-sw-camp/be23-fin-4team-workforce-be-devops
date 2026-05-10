package com._team._team.salary.repository;

import com._team._team.salary.domain.Payroll;
import com._team._team.salary.domain.enums.PayrollStatus;
import com._team._team.salary.domain.enums.PayrollType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayrollRepository extends JpaRepository<Payroll, UUID>, PayrollRepositoryQuerydsl {

    /**
     * 급여대장 단건 조회 (회사 + 삭제되지 않은 것만)
     * - companyId로 타사 접근 차단
     */
    Optional<Payroll> findByPayrollIdAndCompanyIdAndDelYn(UUID payrollId, UUID companyId, String delYn);

    /**
     * 직원별 급여대장 목록 조회 (회사 + 삭제되지 않은 것만, 최신순)
     */
    List<Payroll> findByMemberIdAndCompanyIdAndDelYnOrderByPayrollYearMonthDayDesc(UUID memberId, UUID companyId, String delYn);

    /**
     * 직원 + 정산일 중복 체크 (삭제되지 않은 것만)
     * - 같은 직원이 같은 정산일에 급여대장을 중복 생성하는 것을 방지
     */
    boolean existsByMemberIdAndPayrollYearMonthDayAndDelYn(UUID memberId, LocalDate payrollYearMonthDay, String delYn);


    /**
     * 직원 + 정산일 기존 대장 조회 (delYn 무관)
     * - Unique Constraint 검사용
     * - 존재하면 재생성 불가 (회계 원칙: 삭제 후 재생성 금지)
     */
    Optional<Payroll> findByCompanyIdAndMemberIdAndPayrollYearMonthDay(
            UUID companyId, UUID memberId, LocalDate payrollYearMonthDay);

    // 직원 + 정산일 + payrollType 조합 조회 - 보너스 종류별 충돌 확인
    Optional<Payroll> findByCompanyIdAndMemberIdAndPayrollYearMonthDayAndPayrollType(
            UUID companyId, UUID memberId, LocalDate payrollYearMonthDay, PayrollType payrollType);

    /**
     * 직원별 + 상태별 급여대장 조회 (회사 + 삭제되지 않은 것만)
     */
    List<Payroll> findByMemberIdAndPayrollStatusAndCompanyIdAndDelYn(UUID memberId, PayrollStatus payrollStatus, UUID companyId, String delYn);

    /**
     * 급여(Salary) ID로 급여대장 조회
     * - 특정 급여 정책에 연결된 급여대장들을 조회
     */
    List<Payroll> findBySalaryIdAndDelYn(UUID salaryId, String delYn);

    /**
     * 급여대장을 상태·삭제여부로 전부 조회
     */
    List<Payroll> findByPayrollStatusAndDelYn(PayrollStatus payrollStatus, String delYn);

    // 회사 단위 처리 필요(지급전, 확정 상태인 급여) 급여대장 시간 무관 조회
    List<Payroll> findByCompanyIdAndPayrollStatusInAndDelYnOrderByPayrollYearMonthDayAsc(
            UUID companyId, Collection<PayrollStatus> statuses, String delYn);

    /**
     * [미사용수당 API 용]
     * 특정 연/월에 해당하는 DRAFT 급여대장 조회
     */
    @Query("SELECT p FROM Payroll p " +
            "WHERE p.companyId = :companyId " +
            "AND p.memberId = :memberId " +
            "AND p.payrollYearMonthDay BETWEEN :from AND :to " +
            "AND p.payrollStatus = :status " +
            "AND p.delYn = 'N'")
    Optional<Payroll> findDraftByCompanyMemberAndMonth(
            @Param("companyId") UUID companyId,
            @Param("memberId") UUID memberId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") PayrollStatus status);

    /**
     * [배치용] 회사 + 직원들, 다건의 특정 월 DRAFT Payroll 한 번에 조회
     */
    @Query("""
        SELECT DISTINCT p FROM Payroll p
        LEFT JOIN FETCH p.payrollItemList
        WHERE p.companyId = :companyId
          AND p.memberId IN :memberIds
          AND p.payrollYearMonthDay BETWEEN :from AND :to
          AND p.payrollStatus = :status
          AND p.delYn = 'N'
        """)
    List<Payroll> findDraftsByCompanyMemberIdsAndMonthFetchItems(
            @Param("companyId") UUID companyId,
            @Param("memberIds") Collection<UUID> memberIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") PayrollStatus status);

    // 회사 + 월 범위 totalPayment SUM 산재 회사부담 추정용
    @Query("""
        SELECT COALESCE(SUM(p.totalPayment), 0)
        FROM Payroll p
        WHERE p.companyId = :companyId
          AND p.payrollYearMonthDay BETWEEN :from AND :to
          AND p.delYn = 'N'
        """)
    long sumTotalPaymentInMonth(@Param("companyId") UUID companyId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);

    // 직원 본인 연간 연봉조회 화면용
    @Query("""
        SELECT DISTINCT p FROM Payroll p
        LEFT JOIN FETCH p.payrollItemList
        WHERE p.companyId = :companyId
          AND p.memberId = :memberId
          AND p.payrollYearMonthDay BETWEEN :from AND :to
          AND p.delYn = 'N'
        ORDER BY p.payrollYearMonthDay ASC
        """)
    List<Payroll> findInYearByMemberFetchItems(@Param("companyId") UUID companyId,
                                               @Param("memberId") UUID memberId,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    /**
     *  (정산 대상 월) 기반 직원 연봉 조회용
     */
    @Query("""
        SELECT DISTINCT p FROM Payroll p
        LEFT JOIN FETCH p.payrollItemList
        WHERE p.companyId = :companyId
          AND p.memberId = :memberId
          AND p.targetYearMonth IS NOT NULL
          AND p.targetYearMonth BETWEEN :fromYm AND :toYm
          AND p.delYn = 'N'
        ORDER BY p.targetYearMonth ASC
        """)
    List<Payroll> findByTargetYearMonthRangeFetchItems(@Param("companyId") UUID companyId,
                                                      @Param("memberId") UUID memberId,
                                                      @Param("fromYm") String fromYm,
                                                      @Param("toYm") String toYm);
}
