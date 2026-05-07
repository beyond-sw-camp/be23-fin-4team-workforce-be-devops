package com._team._team.esg.repository;

import com._team._team.esg.domain.EsgActivity;
import com._team._team.esg.domain.enums.ActivityStatus;
import com._team._team.esg.domain.enums.EsgCategory;
import com._team._team.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EsgActivityRepository extends JpaRepository<EsgActivity, UUID> {

    // 회사 전체 활동 목록 (관리자용)
    @Query("SELECT a FROM EsgActivity a JOIN FETCH a.member JOIN FETCH a.subject " +
            "WHERE a.subject.company.companyId = :companyId " +
            "ORDER BY a.createdAt DESC")
    List<EsgActivity> findAllByCompanyId(@Param("companyId") UUID companyId);

    // 회사 전체 활동 목록 - 상태 필터
    @Query("SELECT a FROM EsgActivity a JOIN FETCH a.member JOIN FETCH a.subject " +
            "WHERE a.subject.company.companyId = :companyId AND a.status = :status " +
            "ORDER BY a.createdAt DESC")
    List<EsgActivity> findAllByCompanyIdAndStatus(@Param("companyId") UUID companyId,
                                                  @Param("status") ActivityStatus status);

    // 직원 본인 활동 목록
    List<EsgActivity> findByMemberOrderByCreatedAtDesc(Member member);

    // 직원 본인 활동 목록 - 상태 필터
    List<EsgActivity> findByMemberAndStatusOrderByCreatedAtDesc(Member member, ActivityStatus status);

    // 월별 점수 집계용
    @Query("SELECT a FROM EsgActivity a " +
            "WHERE a.subject.company.companyId = :companyId " +
            "AND a.status = 'APPROVED' " +
            "AND FUNCTION('DATE_FORMAT', a.createdAt, '%Y-%m') = :yearMonth")
    List<EsgActivity> findApprovedByCompanyIdAndYearMonth(@Param("companyId") UUID companyId,
                                                          @Param("yearMonth") String yearMonth);

    // 직원 개인 월별 집계용
    @Query("SELECT a FROM EsgActivity a " +
            "WHERE a.member = :member " +
            "AND a.status = 'APPROVED' " +
            "AND FUNCTION('DATE_FORMAT', a.createdAt, '%Y-%m') = :yearMonth")
    List<EsgActivity> findApprovedByMemberAndYearMonth(@Param("member") Member member,
                                                       @Param("yearMonth") String yearMonth);
}