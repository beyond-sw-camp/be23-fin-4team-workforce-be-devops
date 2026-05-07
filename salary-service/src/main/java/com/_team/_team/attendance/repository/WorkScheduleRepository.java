package com._team._team.attendance.repository;

import com._team._team.attendance.domain.WorkSchedule;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, UUID> {

    /**
     * 회사 소속 근무 스케줄 목록 (삭제 여부 필터)
     * <p>
     * Spring Data JPA 파생 쿼리: {@code companyId} + {@code delYn} 로 필터링.
     * 용도: 관리 화면에서 해당 회사의 스케줄 마스터를 나열하거나, 삭제되지 않은({@code delYn = 'N'}) 건만 조회할 때.
     * 멀티테넌시(회사 단위 데이터 분리)와 소프트 삭제를 동시에 만족시키기 위해 회사 ID와 삭제 플래그를 함께 둠.
     */
    List<WorkSchedule> findByCompanyIdAndDelYn(UUID companyId, String delYn);

    /**
     * 회사 + 스케줄 ID 단건 조회 (삭제 여부 포함)
     * <p>
     * 파생 쿼리: 동일 {@code workScheduleId} 가 다른 회사에 존재할 수 없도록, 조회 시 항상 {@code companyId} 로 스코프를 제한.
     * {@code delYn} 으로 소프트 삭제된 스케줄은 제외하거나, 감사/복구 목적으로 {@code 'Y'} 를 넘겨 조회 가능.
     * 상세/수정 API에서 “이 회사의 이 스케줄”을 안전하게 가져올 때 사용 ({@link Optional} 로 미존재·삭제 건은 빈 값 처리).
     */
    Optional<WorkSchedule> findByCompanyIdAndWorkScheduleIdAndDelYn(UUID companyId, UUID scheduleId, String delYn);

    /**
     * 특정 날짜에 유효한 스케줄 조회
     * - memberId 매칭 우선, null은 (회사 기본)
     */
    @Query("SELECT ws FROM WorkSchedule ws " +
            "WHERE ws.companyId = :companyId " +
            "AND (ws.memberId = :memberId OR ws.memberId IS NULL) " +
            "AND ws.effectiveFrom <= :date " +
            "AND (ws.effectiveTo IS NULL OR ws.effectiveTo >= :date) " +
            "AND ws.delYn = 'N' " +
            "ORDER BY ws.memberId DESC NULLS LAST")
    List<WorkSchedule> findActiveSchedules(
            @Param("companyId") UUID companyId,
            @Param("memberId") UUID memberId,
            @Param("date") LocalDate date);

    /**
     * 개인 스케줄 존재 여부 확인
     */
    @Query("""
       SELECT w FROM WorkSchedule w
       WHERE w.memberId = :memberId
         AND w.effectiveFrom <= :date
         AND (w.effectiveTo IS NULL OR w.effectiveTo >= :date)
         AND w.delYn = 'N'
       ORDER BY w.effectiveFrom DESC
       """)
    Optional<WorkSchedule> findEffectivePersonal(@Param("memberId") UUID memberId,
                                                 @Param("date") LocalDate date);

    /**
     * 회사 기본 스케줄 (memberId IS NULL)
     */
    @Query("""
       SELECT w FROM WorkSchedule w
       WHERE w.companyId = :companyId
         AND w.memberId IS NULL
         AND w.effectiveFrom <= :date
         AND (w.effectiveTo IS NULL OR w.effectiveTo >= :date)
         AND w.delYn = 'N'
       ORDER BY w.effectiveFrom DESC
       """)
    Optional<WorkSchedule> findEffectiveCompany(@Param("companyId") UUID companyId,
                                                @Param("date") LocalDate date);

    /** 시차출퇴근제 스케줄 이면서 특정 일자가 스케줄 슬롯 선택 마감일인 활성 스케줄 목록 */
    @Query("""
       SELECT w FROM WorkSchedule w
       WHERE w.workType = 'FLEXIBLE'
         AND w.selectionDeadlineDay = :day
         AND w.delYn = 'N'
       """)
    List<WorkSchedule> findActiveFlexibleWithDeadlineDay(@Param("day") int day);
}
