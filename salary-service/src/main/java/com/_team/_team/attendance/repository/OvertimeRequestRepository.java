package com._team._team.attendance.repository;

import com._team._team.attendance.domain.OvertimeRequest;
import com._team._team.attendance.domain.enums.OvertimeApprovalStatus;
import com._team._team.attendance.domain.enums.OvertimeRequestType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OvertimeRequestRepository extends JpaRepository<OvertimeRequest, UUID>, OvertimeRequestRepositoryQuerydsl {

    /**
     * 해당 일자의 승인된 OverTime 합계 (부분 승인 고려해 approvedMinutes 합산)
     */
    @Query("""
           SELECT COALESCE(SUM(r.approvedMinutes), 0)
           FROM OvertimeRequest r
           WHERE r.memberId = :memberId
             AND r.targetDate = :date
             AND r.approvalStatus = 'APPROVED'
           """)
    Integer sumApprovedMinutes(@Param("memberId") UUID memberId,
                               @Param("date") LocalDate date);

    /**
     * 주 단위 승인 OverTime 누계 (52시간 검증용)
     * 주의 시작일 ~ 종료일 범위로 합산
     */
    @Query("""
           SELECT COALESCE(SUM(r.approvedMinutes), 0)
           FROM OvertimeRequest r
           WHERE r.memberId = :memberId
             AND r.targetDate BETWEEN :weekStart AND :weekEnd
             AND r.approvalStatus = 'APPROVED'
           """)
    Integer sumApprovedMinutesInRange(@Param("memberId") UUID memberId,
                                      @Param("weekStart") LocalDate weekStart,
                                      @Param("weekEnd") LocalDate weekEnd);

    // 관리자 결재 대기 목록
    Page<OvertimeRequest> findAllByCompanyIdAndApprovalStatus(
            UUID companyId, OvertimeApprovalStatus approvalStatus, Pageable pageable);

    // 개인 신청 내역
    Page<OvertimeRequest> findAllByMemberIdOrderBySubmittedAtDesc(UUID memberId, Pageable pageable);

    // 특정 일자 개인 OverTime 신청 전체 (중복 제출 체크 포함)
    List<OvertimeRequest> findAllByMemberIdAndTargetDate(UUID memberId, LocalDate targetDate);

    /**
     * 만료 배치 대상
     * requestType=POST, approvalStatus=PENDING, submittedAt + 72h 경과
     */
    List<OvertimeRequest> findAllByRequestTypeAndApprovalStatusAndSubmittedAtBefore(
            OvertimeRequestType requestType,
            OvertimeApprovalStatus approvalStatus,
            LocalDateTime submittedBefore);

    // 단건 조회
    Optional<OvertimeRequest> findByOvertimeRequestIdAndCompanyId(UUID overtimeRequestId, UUID companyId);

    // Kafka 이벤트 수신 시 approvalRequestId 로 OvertimeRequest 찾기
    Optional<OvertimeRequest> findByApprovalRequestId(UUID approvalRequestId);
}


