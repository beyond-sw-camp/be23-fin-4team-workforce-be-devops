package com._team._team.esg.repository;
import com._team._team.esg.domain.EsgScore;
import com._team._team.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EsgScoreRepository extends JpaRepository<EsgScore, UUID> {

    // 직원 개인 월별 스냅샷
    Optional<EsgScore> findByMemberAndYearMonth(Member member, String yearMonth);

    // 직원 전체 이력
    List<EsgScore> findByMemberOrderByYearMonthDesc(Member member);

    // 회사 전체 월별 스냅샷 (member = null)
    Optional<EsgScore> findByCompanyIdAndMemberIsNullAndYearMonth(UUID companyId, String yearMonth);

    // 회사 전체 이력
    List<EsgScore> findByCompanyIdAndMemberIsNullOrderByYearMonthDesc(UUID companyId);
}