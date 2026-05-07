package com._team._team.attendance.repository;

import com._team._team.attendance.domain.LeaveRequest;
import com._team._team.attendance.domain.enums.LeaveApprovalStatus;
import com._team._team.attendance.domain.enums.LeaveInitiator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID>, LeaveRequestRepositoryQuerydsl {

    // 본인 특정일 휴가 신청 목록 (같은 날 여러 건 가능)
    List<LeaveRequest> findAllByMemberIdAndStartDateAndDelYn(
            UUID memberId, LocalDate startDate, String delYn);

    // 역조회 (결재 ID 로)
    Optional<LeaveRequest> findByApprovalRequestId(UUID approvalRequestId);

    // 본인 소유 휴가인지 검증
    Optional<LeaveRequest> findByLeaveRequestIdAndMemberId(
            UUID leaveRequestId, UUID memberId);

    // 관리자 단건 조회, 회사 소속 검증
    Optional<LeaveRequest> findByLeaveRequestIdAndCompanyIdAndDelYn(
            UUID leaveRequestId, UUID companyId, String delYn);

    /**
     * 근태 마감 배치용, 특정 날짜에 적용되는 승인된 휴가 조회
     */
    @Query("""
           SELECT l FROM LeaveRequest l
           WHERE l.companyId = :companyId
             AND l.memberId = :memberId
             AND l.approvalStatus = 'APPROVED'
             AND l.startDate <= :date
             AND l.endDate >= :date
             AND l.delYn = 'N'
           """)
    Optional<LeaveRequest> findActiveOnDate(
            @Param("companyId") UUID companyId,
            @Param("memberId") UUID memberId,
            @Param("date") LocalDate date);

    /**
     * 연간 사용량 집계, 시작일이 기간 내인 승인 휴가 합산
     */
    @Query("""
           SELECT COALESCE(SUM(l.usageDays), 0)
           FROM LeaveRequest l
           WHERE l.memberId = :memberId
             AND l.companyId = :companyId
             AND l.companyLeaveTypeId = :companyLeaveTypeId
             AND l.approvalStatus = 'APPROVED'
             AND l.startDate >= :rangeStart
             AND l.startDate <= :rangeEnd
             AND l.delYn = 'N'
           """)
    Double sumUsedDaysInPeriod(
            @Param("memberId") UUID memberId,
            @Param("companyId") UUID companyId,
            @Param("companyLeaveTypeId") UUID companyLeaveTypeId,
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd);

    /**
     * 특정 날짜에 적용되는 모든 승인 휴가 조회, 전 직원 (근태 마감 배치 사용)
     */
    @Query("""
       SELECT l FROM LeaveRequest l
       WHERE l.approvalStatus = 'APPROVED'
         AND l.startDate <= :date
         AND l.endDate >= :date
         AND l.delYn = 'N'
       """)
    List<LeaveRequest> findAllActiveOnDate(@Param("date") LocalDate date);

    /**
     * 본인의 기간과 겹치는 모든 승인 휴가
     */
    @Query("""
       SELECT l FROM LeaveRequest l
       WHERE l.memberId = :memberId
         AND l.approvalStatus = 'APPROVED'
         AND l.startDate <= :to
         AND l.endDate >= :from
         AND l.delYn = 'N'
       """)
    List<LeaveRequest> findApprovedOverlapping(@Param("memberId") UUID memberId,
                                                @Param("from") LocalDate from,
                                                @Param("to") LocalDate to);

    // 회사가 강제 지정한 연차가 해당 일자에 존재하는지
    @Query("""
    SELECT COUNT(r) > 0 FROM LeaveRequest r
    WHERE r.memberId = :memberId
      AND r.companyId = :companyId
      AND r.startDate <= :date
      AND r.endDate >= :date
      AND r.initiator = :initiator
      AND r.approvalStatus = :status
      AND r.delYn = 'N'
    """)
    boolean existsForcedDesignationOnDate(@Param("memberId") UUID memberId,
                                          @Param("companyId") UUID companyId,
                                          @Param("date") LocalDate date,
                                          @Param("initiator") LeaveInitiator initiator,
                                          @Param("status") LeaveApprovalStatus status);
}
