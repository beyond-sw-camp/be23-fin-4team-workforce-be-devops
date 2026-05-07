package com._team._team.attendance.repository;

import com._team._team.attendance.domain.AttendanceLog;
import com._team._team.attendance.domain.DailyAttendance;
import com._team._team.attendance.domain.enums.EventType;
import com._team._team.attendance.domain.enums.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 출퇴근 이벤트 로그 Repository
 *
 * - 이 테이블은 쓰기 빈도가 가장 높음 (직원당 하루 2~4건)
 * - idx_log_member_event_time: 기간별 로그 조회 최적화
 * - idx_log_daily_attendance: 일별 상세 로그 JOIN 최적화
 */
@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, UUID> {

    /** 일별 상세 로그 (시간순) - 근태 상세 조회 시 사용 */
    List<AttendanceLog> findByDailyAttendanceDailyAttendanceIdOrderByEventTime(
            UUID dailyAttendanceId);

    /**
     * 특정 이벤트 타입 존재 여부 (CLOCK_IN/CLOCK_OUT 중복 체크)
     * - EXISTS 서브쿼리로 변환 → 전체 COUNT보다 빠름
     * - 출근/퇴근은 하루 1회이므로 첫 행 발견 즉시 true 리턴
     */
    boolean existsByDailyAttendanceAndEventType(
            DailyAttendance dailyAttendance, EventType eventType);

    /**
     * 특정 이벤트 타입의 가장 최근 로그 1건 조회
     * - BREAK_END 처리 시 마지막 BREAK_START 시간 조회용
     * - ORDER BY event_time DESC LIMIT 1 → 인덱스 활용 단건
     */
    Optional<AttendanceLog> findTop1ByDailyAttendanceAndEventTypeOrderByEventTimeDesc(
            DailyAttendance dailyAttendance, EventType eventType);

    /**
     * 정정 채널(ADMIN_MANUAL) 로 들어온 특정 이벤트의 가장 최근 로그
     * 정정 신청 시 기존 정정 로그 갱신 / 반려 시 정정 로그 식별 용도
     */
    Optional<AttendanceLog> findTop1ByDailyAttendanceAndEventTypeAndSourceTypeOrderByEventTimeDesc(
            DailyAttendance dailyAttendance, EventType eventType, SourceType sourceType);

    /**
     * 원본 채널(WEB/MOBILE/READER) 의 특정 이벤트 가장 최근 로그
     * 반려 시 firstClockIn / lastClockOut 복구용
     */
    Optional<AttendanceLog> findTop1ByDailyAttendanceAndEventTypeAndSourceTypeNotOrderByEventTimeDesc(
            DailyAttendance dailyAttendance, EventType eventType, SourceType excludedSourceType);

    /**
     * 근태 검증 (ADMIN_MANUAL) 로그 일괄 조회
     */
    @Query("""
       SELECT log FROM AttendanceLog log
       JOIN FETCH log.dailyAttendance da
       WHERE da.dailyAttendanceId IN :daIds
         AND log.sourceType = :sourceType
       """)
    List<AttendanceLog> findAdminManualByDailyAttendanceIds(
            @Param("daIds") Collection<UUID> daIds,
            @Param("sourceType") SourceType sourceType);

    /** 호출자 편의 - ADMIN_MANUAL 고정 조회 */
    default List<AttendanceLog> findAdminManualByDailyAttendanceIds(Collection<UUID> daIds) {
        return findAdminManualByDailyAttendanceIds(daIds, SourceType.ADMIN_MANUAL);
    }
}
