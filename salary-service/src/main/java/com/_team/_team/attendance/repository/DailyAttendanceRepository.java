package com._team._team.attendance.repository;

import com._team._team.attendance.domain.DailyAttendance;
import com._team._team.attendance.domain.enums.AttendanceStatus;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import com._team._team.attendance.domain.enums.ClosureStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 일별 근태 Repository
 */
@Repository
public interface DailyAttendanceRepository extends JpaRepository<DailyAttendance, UUID> {

    // 개인 특정일 근태 1건 조회
    Optional<DailyAttendance> findByCompanyIdAndMemberIdAndAttendanceDate(
            UUID companyId, UUID memberId, LocalDate attendanceDate);

    /**
     * 개인 월간 근태 페이징 조회, 직원 본인 근태 이력 화면용
     */
    @Query("SELECT da FROM DailyAttendance da " +
            "WHERE da.memberId = :memberId " +
            "AND da.attendanceDate BETWEEN :from AND :to " +
            "ORDER BY da.attendanceDate")
    Page<DailyAttendance> findMonthlyByMember(
            @Param("memberId") UUID memberId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    /**
     * 관리자 대시보드(오늘 출근/미출근), 현장 출근 현황, 일별 집계의 입력 데이터
     */
    @Query("SELECT da FROM DailyAttendance da " +
            "WHERE da.companyId = :companyId " +
            "AND da.attendanceDate = :date " +
            "ORDER BY da.memberId")
    Page<DailyAttendance> findDailyByCompany(
            @Param("companyId") UUID companyId,
            @Param("date") LocalDate date,
            Pageable pageable);

    /**
     * 회사 월간 근태 페이징, 관리자 월별 근태 리포트 화면용
     */
    @Query("SELECT da FROM DailyAttendance da " +
            "WHERE da.companyId = :companyId " +
            "AND da.attendanceDate BETWEEN :from AND :to " +
            "ORDER BY da.attendanceDate, da.memberId")
    Page<DailyAttendance> findMonthlyByCompany(
            @Param("companyId") UUID companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    /**
     * 개인 기간 근태 , 집계용
     */
    @Query("SELECT da FROM DailyAttendance da " +
            "WHERE da.memberId = :memberId " +
            "AND da.companyId = :companyId " +
            "AND da.attendanceDate BETWEEN :from AND :to " +
            "ORDER BY da.attendanceDate")
    List<DailyAttendance> findByMemberAndPeriod(
            @Param("memberId") UUID memberId,
            @Param("companyId") UUID companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // 자동 마감 후보 조회, 출근 찍었는데 퇴근 안 찍힌 건
    @Query("SELECT da FROM DailyAttendance da " +
            "WHERE da.attendanceDate = :date " +
            "AND da.firstClockIn IS NOT NULL " +
            "AND da.lastClockOut IS NULL")
    List<DailyAttendance> findCloseCandidates(@Param("date") LocalDate date);

    // 휴가일 출근 감지, LEAVE/HALF 상태인데 출근 찍힌 건 조회
    @Query("SELECT da FROM DailyAttendance da " +
            "WHERE da.attendanceDate = :date " +
            "AND da.status IN :statuses " +
            "AND da.firstClockIn IS NOT NULL")
    List<DailyAttendance> findLeaveDateWithClockIn(
            @Param("date") LocalDate date,
            @Param("statuses") List<AttendanceStatus> statuses);

    // 특정일 전사 근태 전체
    @Query("SELECT da FROM DailyAttendance da WHERE da.attendanceDate = :date")
    List<DailyAttendance> findAllByAttendanceDate(@Param("date") LocalDate date);

    /**
     * 월마감 배치에서 “확정된 일자만” 월별 집계 테이블이나 급여 입력으로 넘길 때 사용, 미확정 행은 제외
     */
    @Query("""
       SELECT d FROM DailyAttendance d
       WHERE d.companyId = :companyId
         AND d.attendanceDate BETWEEN :from AND :to
         AND d.closureStatus = 'FINALIZED'
       """)
    List<DailyAttendance> findFinalizedInMonth(@Param("companyId") UUID companyId,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    /**
     * 월마감 가능 여부 사전 검사: 미해결 건 수
     */
    @Query("""
       SELECT COUNT(d) FROM DailyAttendance d
       WHERE d.companyId = :companyId
         AND d.attendanceDate BETWEEN :from AND :to
         AND d.closureStatus IN ('DRAFT', 'UNDER_REVIEW', 'OPEN')
       """)
    long countUnresolvedInMonth(@Param("companyId") UUID companyId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);

    /**
     * 일마감 Draft 배치 대상
     * 특정 날짜의 OPEN 상태 근태 (배치가 DRAFT로 전이)
     */
    List<DailyAttendance> findAllByCompanyIdAndAttendanceDateAndClosureStatus(
            UUID companyId, LocalDate attendanceDate, ClosureStatus closureStatus);

    /**
     * 개인 월간 근태 (기간 정해서)
     */
    @Query("""
       SELECT d FROM DailyAttendance d
       WHERE d.memberId = :memberId
         AND d.attendanceDate BETWEEN :from AND :to
       ORDER BY d.attendanceDate
       """)
    List<DailyAttendance> findAllByMemberInRange(@Param("memberId") UUID memberId,
                                                 @Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);

    /**
     * 특정 일자의 특정 closureStatus 인 근태 전체 (전사)
     * Draft 배치 OPEN 조회, Final 배치 DRAFT 조회에 사용
     */
    List<DailyAttendance> findAllByAttendanceDateAndClosureStatus(
            LocalDate attendanceDate,
            ClosureStatus closureStatus);

    /** 전월 근태가 있는 회사 ID 목록 (배치 대상 회사 선별) */
    @Query("""
       SELECT DISTINCT d.companyId FROM DailyAttendance d
       WHERE d.attendanceDate BETWEEN :from AND :to
       """)
    List<UUID> findDistinctCompanyIdsInRange(@Param("from") LocalDate from,
                                             @Param("to") LocalDate to);


    Optional<DailyAttendance> findByMemberIdAndAttendanceDate(
            UUID memberId, LocalDate attendanceDate);

    /**
     * 정정 검토
     * 관리자 정정 검토 화면에서 사용
     */
    @Query("""
       SELECT d FROM DailyAttendance d
       WHERE d.companyId = :companyId
         AND d.closureStatus = :status
       ORDER BY d.attendanceDate DESC, d.memberId
       """)
    List<DailyAttendance> findAllByCompanyIdAndClosureStatusOrderByDateDesc(
            @Param("companyId") UUID companyId,
            @Param("status") ClosureStatus status);

    /**
     * 회사 단위 직원별 실측 추가근무 시간 합산
     * [초과 근무 현황] 화면 - 주/월 한도 위반 모니터링
     */
    @Query("""
       SELECT d.memberId AS memberId, COALESCE(SUM(d.overtimeMinutes), 0) AS sumMinutes
       FROM DailyAttendance d
       WHERE d.companyId = :companyId
         AND d.attendanceDate BETWEEN :from AND :to
       GROUP BY d.memberId
       """)
    List<MemberOvertimeMinutesRow> sumActualOvertimeByCompanyAndRange(
            @Param("companyId") UUID companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** 회사 단위 직원별 실측 OT 합산 결과 row */
    interface MemberOvertimeMinutesRow {
        UUID getMemberId();
        Long getSumMinutes();
    }

    // 회사 단위 직원별 총 근무시간 합산 (정규 + 초과 포함, 퇴근-출근 기반)
    // 초과 근무 현황 화면의 '이번 달 총 근무시간' 컬럼 용도
    @Query("""
       SELECT d.memberId AS memberId, COALESCE(SUM(d.workedMinutes), 0) AS sumMinutes
       FROM DailyAttendance d
       WHERE d.companyId = :companyId
         AND d.attendanceDate BETWEEN :from AND :to
       GROUP BY d.memberId
       """)
    List<MemberOvertimeMinutesRow> sumWorkedByCompanyAndRange(
            @Param("companyId") UUID companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // 회사 단위 직원별 + 일자별 worked/overtime 분 (초과 근무 현황 화면 일자별 셀 용도)
    @Query("""
       SELECT d.memberId AS memberId,
              d.attendanceDate AS attendanceDate,
              COALESCE(d.workedMinutes, 0) AS workedMinutes,
              COALESCE(d.overtimeMinutes, 0) AS overtimeMinutes
       FROM DailyAttendance d
       WHERE d.companyId = :companyId
         AND d.attendanceDate BETWEEN :from AND :to
       """)
    List<MemberDailyMinutesRow> findDailyMinutesByCompanyAndRange(
            @Param("companyId") UUID companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    interface MemberDailyMinutesRow {
        UUID getMemberId();
        LocalDate getAttendanceDate();
        Integer getWorkedMinutes();
        Integer getOvertimeMinutes();
    }
}
