package com._team._team.salary.repository;

import com._team._team.salary.domain.Salary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SalaryRepository extends JpaRepository<Salary, UUID> {

    /** 회사별 급여 목록 조회 */
    List<Salary> findByCompanyId(UUID companyId);

    /** 직원별 급여 이력 조회(회사별) */
    List<Salary> findByMemberIdAndCompanyId(UUID memberId, UUID companyId);

    /**
     * 직원의 특정 날짜 기준 적용 중인 급여 조회
     */
    @Query("SELECT s FROM Salary s " +
           "WHERE s.memberId = :memberId " +
           "AND s.companyId = :companyId " +
           "AND s.effectiveFrom <= :date " +
           "AND (s.effectiveTo IS NULL OR s.effectiveTo >= :date)")
    Optional<Salary> findActiveSalary(@Param("memberId") UUID memberId,
                                      @Param("companyId") UUID companyId,
                                      @Param("date") LocalDate date);

    /**
     * 정산기간과 겹치는 Salary 조회
     * 월 중간 퇴사여도 그 달 정산기간에 일부 active 였던 Salary 를 찾아 일할 계산에 사용
     */
    @Query("""
        SELECT s FROM Salary s
        WHERE s.memberId = :memberId
          AND s.companyId = :companyId
          AND s.effectiveFrom <= :periodEnd
          AND (s.effectiveTo IS NULL OR s.effectiveTo >= :periodStart)
        ORDER BY s.effectiveFrom DESC
        """)
    List<Salary> findSalariesOverlappingPeriod(
            @Param("memberId") UUID memberId,
            @Param("companyId") UUID companyId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);

    // 급여 계산 배치시 회사 ID 조회
    @Query("SELECT DISTINCT s.companyId FROM Salary s")
    List<UUID> findDistinctCompanyIds();

    /**
     * [배치용] 회사 + 직원들, 다건의 활성 Salary 한번에 조회.
     */
    @Query("""
        SELECT s FROM Salary s
        WHERE s.companyId = :companyId
          AND s.memberId IN :memberIds
          AND s.effectiveFrom <= :date
          AND (s.effectiveTo IS NULL OR s.effectiveTo >= :date)
        """)
    List<Salary> findActiveSalariesByMemberIds(
            @Param("companyId") UUID companyId,
            @Param("memberIds") Collection<UUID> memberIds,
            @Param("date") LocalDate date);

    /**
     * 퇴직 - 직원의 활성/미래 Salary 행을 retireDate 로 일괄 종료
     */
    @Modifying
    @Query("""
        UPDATE Salary s
           SET s.effectiveTo = :retireDate
         WHERE s.memberId = :memberId
           AND s.companyId = :companyId
           AND (s.effectiveTo IS NULL OR s.effectiveTo > :retireDate)
        """)
    int closeActiveByMemberOnRetire(
            @Param("memberId") UUID memberId,
            @Param("companyId") UUID companyId,
            @Param("retireDate") LocalDate retireDate);
}
