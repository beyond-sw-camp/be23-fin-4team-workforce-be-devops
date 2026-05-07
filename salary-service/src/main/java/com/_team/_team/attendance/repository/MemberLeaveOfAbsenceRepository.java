package com._team._team.attendance.repository;

import com._team._team.attendance.domain.MemberLeaveOfAbsence;
import com._team._team.attendance.domain.enums.LeaveOfAbsenceApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberLeaveOfAbsenceRepository
        extends JpaRepository<MemberLeaveOfAbsence, UUID> {

    // 본인 이력, 최근 신청 순
    List<MemberLeaveOfAbsence> findAllByMemberIdAndDelYnOrderByStartDateDesc(
            UUID memberId, String delYn);

    // 본인 소유 검증
    Optional<MemberLeaveOfAbsence> findByLeaveOfAbsenceIdAndMemberId(
            UUID leaveOfAbsenceId, UUID memberId);

    // 관리자 단건 조회 + 회사 검증
    Optional<MemberLeaveOfAbsence> findByLeaveOfAbsenceIdAndCompanyIdAndDelYn(
            UUID leaveOfAbsenceId, UUID companyId, String delYn);

    // 관리자 상태별 목록 (결재 대기, 휴직 중 등)
    List<MemberLeaveOfAbsence> findAllByCompanyIdAndStatusAndDelYnOrderByStartDateAsc(
            UUID companyId, LeaveOfAbsenceApprovalStatus status, String delYn);

    // Kafka Consumer 역조회
    Optional<MemberLeaveOfAbsence> findByApprovalRequestId(UUID approvalRequestId);

    /**
     * 특정 날짜에 휴직 중인 기록 조회
     * 근태 배치, 연차 발생 배치, 급여 계산 에서 사용
     */
    @Query("""
           SELECT a FROM MemberLeaveOfAbsence a
           WHERE a.memberId = :memberId
             AND a.status = 'ACTIVE'
             AND a.startDate <= :date
             AND a.endDate >= :date
             AND a.delYn = 'N'
           """)
    Optional<MemberLeaveOfAbsence> findActiveOnDate(
            @Param("memberId") UUID memberId,
            @Param("date") LocalDate date);

    /**
     * 특정 날짜에 휴직 중인 전 직원 목록 조회
     */
    @Query("""
           SELECT a FROM MemberLeaveOfAbsence a
           WHERE a.status = 'ACTIVE'
             AND a.startDate <= :date
             AND a.endDate >= :date
             AND a.delYn = 'N'
           """)
    List<MemberLeaveOfAbsence> findAllActiveOnDate(@Param("date") LocalDate date);

    /**
     * 자연 종료 배치용, 종료일 지난 ACTIVE 건 전부 조회
     */
    @Query("""
           SELECT a FROM MemberLeaveOfAbsence a
           WHERE a.status = 'ACTIVE'
             AND a.endDate < :today
             AND a.delYn = 'N'
           """)
    List<MemberLeaveOfAbsence> findToEndByDate(@Param("today") LocalDate today);

    /**
     * 회사 전체 특정 날짜 ACTIVE 휴직자 일괄 조회
     */
    @Query("""
       SELECT a FROM MemberLeaveOfAbsence a
       WHERE a.companyId = :companyId
         AND a.status = 'ACTIVE'
         AND a.startDate <= :date
         AND a.endDate >= :date
         AND a.delYn = 'N'
       """)
    List<MemberLeaveOfAbsence> findAllActiveInCompanyOnDate(
            @Param("companyId") UUID companyId,
            @Param("date") LocalDate date);

    /**
     * 정산기간과 겹치는 휴직 조회
     * 급여 계산시 무급 휴직 일수 집계
     */
    @Query("""
       SELECT a FROM MemberLeaveOfAbsence a
       WHERE a.companyId = :companyId
         AND a.memberId = :memberId
         AND a.status IN ('ACTIVE', 'ENDED')
         AND a.startDate <= :periodEnd
         AND a.delYn = 'N'
       """)
    List<MemberLeaveOfAbsence> findInPeriod(
            @Param("companyId") UUID companyId,
            @Param("memberId") UUID memberId,
            @Param("periodEnd") LocalDate periodEnd);
}