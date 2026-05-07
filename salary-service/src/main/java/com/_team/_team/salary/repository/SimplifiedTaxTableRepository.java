package com._team._team.salary.repository;

import com._team._team.salary.domain.SimplifiedTaxTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SimplifiedTaxTableRepository extends JpaRepository<SimplifiedTaxTable, UUID> {

    // 연도 + 월급여 + 부양가족수 기준 소득세 구간 조회
    @Query("""
           SELECT t FROM SimplifiedTaxTable t
           WHERE t.effectiveYear = :year
             AND t.salaryLowerBound <= :salary
             AND t.salaryUpperBound > :salary
             AND t.dependentCount = :dependents
             AND t.delYn = 'N'
           """)
    Optional<SimplifiedTaxTable> findTaxFor(
            @Param("year") Integer year,
            @Param("salary") Long salary,
            @Param("dependents") Integer dependents);

    // 연도별 삭제, 재업로드 시 기존 데이터 삭제
    @Modifying
    @Query("""
           UPDATE SimplifiedTaxTable t SET t.delYn = 'Y'
           WHERE t.effectiveYear = :year AND t.delYn = 'N'
           """)
    int softDeleteByYear(@Param("year") Integer year);

    // hard delete
    // 운영자 재업로드 시 기존 행 완전 제거 후 재등록 용도
    @Modifying
    @Query("DELETE FROM SimplifiedTaxTable t WHERE t.effectiveYear = :year")
    int hardDeleteByYear(@Param("year") Integer year);

    // 등록된 연도 목록 화면 표시용
    @Query("""
           SELECT DISTINCT t.effectiveYear FROM SimplifiedTaxTable t
           WHERE t.delYn = 'N'
           ORDER BY t.effectiveYear DESC
           """)
    List<Integer> findDistinctYears();

    // 연도별 행 수 카운트
    long countByEffectiveYearAndDelYn(Integer effectiveYear, String delYn);
}