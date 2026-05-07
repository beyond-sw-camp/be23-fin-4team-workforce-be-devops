package com._team._team.salary.repository;

import com._team._team.salary.domain.SalaryPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface SalaryPolicyRepository extends JpaRepository<SalaryPolicy, UUID> {

    // 회사별 급여정책 조회
    List<SalaryPolicy> findByCompanyIdAndDelYn(UUID companyId, String delYn);
    // 동일 이름 급여정책 존재여부 확인
    boolean existsByCompanyIdAndPolicyNameAndEffectiveToIsNull(UUID companyId, String policyName);

    /**
     * 특정 날짜 기준 활성 급여정책 조회
     * - ORDER BY effectiveFrom DESC로 가장 최신 정책이 첫 번째
     * - 비어있으면 fallback: 당월 1일~말일
     */
    @Query("SELECT sp FROM SalaryPolicy sp " +
            "WHERE sp.companyId = :companyId " +
            "AND sp.delYn = 'N' " +
            "AND sp.effectiveFrom <= :date " +
            "AND (sp.effectiveTo IS NULL OR sp.effectiveTo >= :date) " +
            "ORDER BY sp.effectiveFrom DESC")
    List<SalaryPolicy> findActivePolicies(
            @Param("companyId") UUID companyId,
            @Param("date") LocalDate date);
}
