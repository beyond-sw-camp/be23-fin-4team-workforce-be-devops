package com._team._team.attendance.repository;

import com._team._team.attendance.domain.WorkTripDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkTripDetailRepository extends JpaRepository<WorkTripDetail, UUID> {

    /** 직원의 출장/외근 이력 */
    @Query("""
        SELECT w FROM WorkTripDetail w
        LEFT JOIN FETCH w.dailyAttendance
        WHERE w.memberId = :memberId
          AND w.companyId = :companyId
          AND w.delYn = :delYn
        """)
    List<WorkTripDetail> findByMemberIdAndCompanyIdAndDelYn(
            @Param("memberId") UUID memberId,
            @Param("companyId") UUID companyId,
            @Param("delYn") String delYn);

    /** 특정일 출장/외근 내역 (일별 근태 상세에서 표시) */
    List<WorkTripDetail> findByMemberIdAndCompanyIdAndDelYnAndDailyAttendanceDailyAttendanceId(
            UUID memberId, UUID companyId, String delYn, UUID dailyAttendanceId);

    /** 단건 조회 (수정/삭제용 — companyId로 소속 검증) */
    Optional<WorkTripDetail> findByWorkTripDetailIdAndMemberIdAndCompanyIdAndDelYn(
            UUID workTripDetailId, UUID memberId, UUID companyId, String delYn);
}
