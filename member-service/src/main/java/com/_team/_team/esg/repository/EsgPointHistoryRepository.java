package com._team._team.esg.repository;

import com._team._team.esg.domain.EsgPointHistory;
import com._team._team.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EsgPointHistoryRepository extends JpaRepository<EsgPointHistory, UUID> {

    // 현재 잔액 조회 — 가장 최근 balance 스냅샷
    @Query("SELECT h FROM EsgPointHistory h " +
            "WHERE h.member = :member " +
            "ORDER BY h.createdAt DESC")
    List<EsgPointHistory> findByMemberOrderByCreatedAtDesc(@Param("member") Member member);

    // 최신 이력 1건 (잔액 확인용)
    @Query("SELECT h FROM EsgPointHistory h " +
            "WHERE h.member = :member " +
            "ORDER BY h.createdAt DESC " +
            "LIMIT 1")
    Optional<EsgPointHistory> findLatestByMember(@Param("member") Member member);

    @Query("SELECT COALESCE(SUM(h.points), 0) FROM EsgPointHistory h " +
            "WHERE h.member = :member " +
            "AND h.pointType = 'EARN' " +
            "AND FUNCTION('DATE_FORMAT', h.createdAt, '%Y-%m') = :yearMonth")
    int findMonthlyEarnedByMember(@Param("member") Member member,
                                  @Param("yearMonth") String yearMonth);
}