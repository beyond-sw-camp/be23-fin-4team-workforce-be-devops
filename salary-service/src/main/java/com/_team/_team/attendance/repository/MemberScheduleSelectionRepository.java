package com._team._team.attendance.repository;
import com._team._team.attendance.domain.MemberScheduleSelection;
import com._team._team.attendance.domain.enums.ScheduleApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberScheduleSelectionRepository  extends JpaRepository<MemberScheduleSelection, UUID> {

    // 최신 APPROVED 또는 AUTO 1건을 현재 유효 선택으로 사용
    // 같은 월에 재신청으로 여러 건 승인된 경우 최신 1건 사용
    @Query("SELECT s FROM MemberScheduleSelection s " +
            "WHERE s.memberId = :memberId " +
            "  AND s.targetYearMonth = :yearMonth " +
            "  AND s.approvalStatus IN ('APPROVED', 'AUTO') " +
            "ORDER BY s.requestedAt DESC")
    List<MemberScheduleSelection> findCurrentActiveList(
            @Param("memberId") UUID memberId,
            @Param("yearMonth") String yearMonth);

    default Optional<MemberScheduleSelection> findCurrentActive(UUID memberId, String yearMonth) {
        return findCurrentActiveList(memberId, yearMonth).stream().findFirst();
    }

    // 중복 제출 방지 (PENDING 건이 이미 있는지 확인)
    boolean existsByMemberIdAndTargetYearMonthAndApprovalStatus(
            UUID memberId, String yearMonth, ScheduleApprovalStatus status);

    // 결재 대기 목록 (관리자 승인 화면)
    List<MemberScheduleSelection> findAllByCompanyIdAndApprovalStatusOrderByRequestedAtAsc(
            UUID companyId, ScheduleApprovalStatus status);

    // 마감일 후 미선택자 배치용
    @Query("SELECT DISTINCT s.memberId FROM MemberScheduleSelection s " +
            "WHERE s.targetYearMonth = :yearMonth " +
            "  AND s.approvalStatus IN ('APPROVED', 'AUTO') " +
            "  AND s.companyId = :companyId")
    List<UUID> findMembersWithActiveSelection(
            @Param("companyId") UUID companyId,
            @Param("yearMonth") String yearMonth);

    // 회사 월별 선택 현황 (대시보드용)
    List<MemberScheduleSelection> findAllByCompanyIdAndTargetYearMonth(
            UUID companyId, String yearMonth);

    // 직원 월별 이력 전체 (PENDING, APPROVED, REJECTED, CANCELLED 모두 포함)
    List<MemberScheduleSelection> findAllByMemberIdAndTargetYearMonth(
            UUID memberId, String targetYearMonth);

    /**
     * 퇴직 cascade
     * 일괄 CANCELLED 로 전환. 이미 지난 월은 history 로 보존.
     */
    @Modifying
    @Query("""
           UPDATE MemberScheduleSelection s
              SET s.approvalStatus = :cancelled
            WHERE s.memberId = :memberId
              AND s.targetYearMonth > :retireYearMonth
              AND s.approvalStatus IN :targetStatuses
           """)
    int cancelFutureSelectionsByMember(
            @Param("memberId") UUID memberId,
            @Param("retireYearMonth") String retireYearMonth,
            @Param("cancelled") ScheduleApprovalStatus cancelled,
            @Param("targetStatuses") List<ScheduleApprovalStatus> targetStatuses);
}
