package com._team._team.attendance.repository;

import com._team._team.attendance.domain.OvertimePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OvertimePolicyRepository extends JpaRepository<OvertimePolicy, UUID> {

    // 특정 일자에 유효한 정책 1건 조회, 삭제된 정책 제외
    @Query("""
           SELECT p FROM OvertimePolicy p
           WHERE p.companyId = :companyId
             AND p.delYn = 'N'
             AND p.effectiveFrom <= :date
             AND (p.effectiveTo IS NULL OR p.effectiveTo >= :date)
           ORDER BY p.effectiveFrom DESC
           """)
    Optional<OvertimePolicy> findEffective(@Param("companyId") UUID companyId,
                                           @Param("date") LocalDate date);

    // 회사 정책 이력 전체, 삭제된 정책 제외
    @Query("""
           SELECT p FROM OvertimePolicy p
           WHERE p.companyId = :companyId
             AND p.delYn = 'N'
           ORDER BY p.effectiveFrom DESC
           """)
    List<OvertimePolicy> findAllByCompanyIdOrderByEffectiveFromDesc(@Param("companyId") UUID companyId);

    // 현재 적용 중인 정책, 삭제된 정책 제외
    @Query("""
           SELECT p FROM OvertimePolicy p
           WHERE p.companyId = :companyId
             AND p.delYn = 'N'
             AND p.effectiveTo IS NULL
           """)
    Optional<OvertimePolicy> findByCompanyIdAndEffectiveToIsNull(@Param("companyId") UUID companyId);
}
