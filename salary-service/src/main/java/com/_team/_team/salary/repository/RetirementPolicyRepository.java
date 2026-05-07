package com._team._team.salary.repository;


import com._team._team.salary.domain.RetirementPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RetirementPolicyRepository extends JpaRepository<RetirementPolicy, UUID> {

    // 회사별 정책 이력 전체 내림차순
    List<RetirementPolicy> findByCompanyIdAndDelYnOrderByEffectiveFromDesc(
            UUID companyId, String delYn);

    /** 회사, 적용시작일 최대 1건 조회 */
    Optional<RetirementPolicy> findByCompanyIdAndEffectiveFrom(UUID companyId, LocalDate effectiveFrom);

    // 특정 시점 활성 정책 단건 조회
    // 동시에 여러 개 활성이면 effectiveFrom 가장 최신 1건 반환
    @Query("SELECT rp FROM RetirementPolicy rp " +
            "WHERE rp.companyId = :companyId " +
            "AND rp.delYn = 'N' " +
            "AND rp.effectiveFrom <= :date " +
            "AND (rp.effectiveTo IS NULL OR rp.effectiveTo >= :date) " +
            "ORDER BY rp.effectiveFrom DESC")
    List<RetirementPolicy> findActivePolicies(
            @Param("companyId") UUID companyId,
            @Param("date") LocalDate date);

    // 활성 정책 단건 조회
    default Optional<RetirementPolicy> findActiveAt(UUID companyId, LocalDate date) {
        List<RetirementPolicy> list = findActivePolicies(companyId, date);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    // 등록 시 기간 겹침 검증용 effectiveFrom 이후 활성 행 존재 여부
    @Query("SELECT COUNT(rp) > 0 FROM RetirementPolicy rp " +
            "WHERE rp.companyId = :companyId " +
            "AND rp.delYn = 'N' " +
            "AND (rp.effectiveTo IS NULL OR rp.effectiveTo >= :effectiveFrom)")
    boolean existsActiveOrFutureAt(
            @Param("companyId") UUID companyId,
            @Param("effectiveFrom") LocalDate effectiveFrom);
}