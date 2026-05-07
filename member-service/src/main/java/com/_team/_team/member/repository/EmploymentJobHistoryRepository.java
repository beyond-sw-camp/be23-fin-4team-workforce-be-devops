package com._team._team.member.repository;

import com._team._team.member.domain.EmploymentJobHistory;
import com._team._team.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmploymentJobHistoryRepository extends JpaRepository<EmploymentJobHistory, UUID> {

    // 직원 이력 전체 조회 (최신순)
    List<EmploymentJobHistory> findByMemberOrderByEffectiveFromDesc(Member member);

    // 현재 적용 중인 이력 조회 (effectiveTo = null)
    @Query("SELECT h FROM EmploymentJobHistory h " +
            "WHERE h.member = :member " +
            "AND h.effectiveTo IS NULL")
    Optional<EmploymentJobHistory> findCurrentHistory(@Param("member") Member member);
}
