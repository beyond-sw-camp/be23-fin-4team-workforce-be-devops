package com._team._team.salary.repository;


import com._team._team.salary.domain.BonusPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BonusPolicyRepository extends JpaRepository<BonusPolicy, UUID> {

    // 회사별 정책 이력 전체 내림차순
    List<BonusPolicy> findByCompanyIdAndDelYnOrderByEffectiveFromDesc(
            UUID companyId, String delYn);

    // 회사, 적용시작일 최대 1건 조회
    Optional<BonusPolicy> findByCompanyIdAndEffectiveFrom(UUID companyId, LocalDate effectiveFrom);

    // 특정 시점 활성 정책 단건 조회
    // 동시에 여러 개 활성이면 effectiveFrom 가장 최신 1건 반환
    @Query("SELECT bp FROM BonusPolicy bp " +
            "WHERE bp.companyId = :companyId " +
            "AND bp.delYn = 'N' " +
            "AND bp.effectiveFrom <= :date " +
            "AND (bp.effectiveTo IS NULL OR bp.effectiveTo >= :date) " +
            "ORDER BY bp.effectiveFrom DESC")
    List<BonusPolicy> findActivePolicies(
            @Param("companyId") UUID companyId,
            @Param("date") LocalDate date);

    // 활성 정책 단건 조회
    default Optional<BonusPolicy> findActiveAt(UUID companyId, LocalDate date) {
        List<BonusPolicy> list = findActivePolicies(companyId, date);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * 정기 상여 사용 + 특정 시점에 활성인 모든 회사 정책 조회
     * 정기상여 배치에서 사용
     */
    @Query("SELECT bp FROM BonusPolicy bp " +
            "WHERE bp.delYn = 'N' " +
            "AND bp.useRegularBonusYn = 'Y' " +
            "AND bp.effectiveFrom <= :date " +
            "AND (bp.effectiveTo IS NULL OR bp.effectiveTo >= :date)")
    List<BonusPolicy> findAllActiveRegularBonusPoliciesAt(@Param("date") LocalDate date);
}
