package com._team._team.attendance.service;

import com._team._team.attendance.domain.AttendanceLog;
import com._team._team.attendance.domain.MemberScheduleSelection;
import com._team._team.attendance.repository.AttendanceLogRepository;
import com._team._team.attendance.repository.DailyAttendanceRepository;
import com._team._team.attendance.repository.MemberScheduleSelectionRepository;
import com._team._team.attendance.repository.WorkScheduleRepository;
import com._team._team.attendance.domain.DailyAttendance;
import com._team._team.attendance.domain.WorkSchedule;
import com._team._team.attendance.domain.enums.AttendanceStatus;
import com._team._team.attendance.domain.enums.ClosureStatus;
import com._team._team.attendance.domain.enums.CorrectionState;
import com._team._team.attendance.domain.enums.WorkType;
import com._team._team.attendance.dto.resDto.DailyAttendanceResDto;

import java.time.format.DateTimeFormatter;
import com._team._team.dto.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;


@Service
@Transactional
public class DailyAttendanceService {
    private final DailyAttendanceRepository dailyAttendanceRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final MemberScheduleSelectionRepository memberScheduleSelectionRepository;

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");

    @Autowired
    public DailyAttendanceService(DailyAttendanceRepository dailyAttendanceRepository,
                                  WorkScheduleRepository workScheduleRepository,
                                  AttendanceLogRepository attendanceLogRepository,
                                  MemberScheduleSelectionRepository memberScheduleSelectionRepository) {
        this.dailyAttendanceRepository = dailyAttendanceRepository;
        this.workScheduleRepository = workScheduleRepository;
        this.attendanceLogRepository = attendanceLogRepository;
        this.memberScheduleSelectionRepository = memberScheduleSelectionRepository;
    }

    /** 근무, 휴게 분 쌍 - 퇴근 시 recalculate 호출에 사용 */
    public record WorkBreakMinutes(int workMinutes, int breakMinutes) {}

    /**
     * 해당 날짜의 일일 근태 조회, 없으면 새로 생성
     * - 출근(clockIn) 시 호출되어 당일 근태 레코드를 보장
     */
    public DailyAttendance findOrCreateDaily(UUID companyId, UUID memberId, LocalDate date){
        // 1. 당일 근태 기록이 이미 있으면 그대로 반환
        Optional<DailyAttendance> existing = dailyAttendanceRepository
                .findByCompanyIdAndMemberIdAndAttendanceDate(companyId, memberId, date);

        if (existing.isPresent()) {
            return existing.get();
        }

        // 2. 없으면 신규 생성
        // 2-1. 유효한 근무 스케줄 조회 (개인 스케줄 우선, 없으면 회사 기본)
        UUID scheduleId = resolveWorkScheduleId(companyId, memberId, date);

        /**
        * 2-2. DailyAttendance 생성
        * status = ABSENT: 아직 출근 로그가 안 찍힌 상태, 이후 clockIn()에서 NORMAL로 즉시 변경됨
        */
        DailyAttendance dailyAttendance = DailyAttendance.builder()
                .companyId(companyId)
                .memberId(memberId)
                .attendanceDate(date)
                .status(AttendanceStatus.ABSENT)
                .workScheduleId(scheduleId)        // 개인 스케줄 우선, 없으면 회사 기본
                .build();
        return dailyAttendanceRepository.save(dailyAttendance);
    }

    /**
     * 해당 날짜의 DailyAttendance 조회 (없으면 예외)
     * 출근 기록 없이 호출되면 에러 (퇴근은 "출근한 상태"에서만 가능)
     */
    @Transactional(readOnly = true)
    public DailyAttendance findDaily(UUID companyId, UUID memberId, LocalDate date){
        return dailyAttendanceRepository
                .findByCompanyIdAndMemberIdAndAttendanceDate(companyId, memberId, date)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.BAD_REQUEST, "오늘 출근 기록이 없습니다. 출근을 먼저 해주세요."));
    }

    /**
     * 특정 날짜 근태 조회 (응답 DTO 변환)
     * - 사원이 자기 근태 확인할 때 사용
     * - 기록이 없는 날(예: 미출근일)은 null 반환
     */
    @Transactional(readOnly = true)
    public DailyAttendanceResDto findDailyAttendance(UUID companyId, UUID memberId, LocalDate date) {
        return dailyAttendanceRepository
                .findByCompanyIdAndMemberIdAndAttendanceDate(companyId, memberId, date)
                .map(DailyAttendanceResDto::fromEntity)
                .orElse(null);
    }

    /**
     * 개인 월간 근태 조회 (페이징)
     * - 사원 본인이 한 달 근태 내역 확인
     */
    @Transactional(readOnly = true)
    public Page<DailyAttendanceResDto> findMonthlyByMember(UUID companyId, UUID memberId, LocalDate from, LocalDate to, Pageable pageable) {
        Page<DailyAttendance> page = dailyAttendanceRepository
                .findMonthlyByMember(memberId, from, to, pageable);
        Map<UUID, CorrectionState> stateMap = computeCorrectionStates(page.getContent());
        return page.map(da -> DailyAttendanceResDto.fromEntity(
                da, stateMap.getOrDefault(da.getDailyAttendanceId(), CorrectionState.NORMAL)));
    }

    /**
     * 회사 전체 특정일 근태 조회 - 관리자 대시보드 (페이징)
     */
    @Transactional(readOnly = true)
    public Page<DailyAttendanceResDto> findDailyByCompany(UUID companyId, LocalDate date, Pageable pageable) {
        Page<DailyAttendance> page = dailyAttendanceRepository
                .findDailyByCompany(companyId, date, pageable);
        Map<UUID, CorrectionState> stateMap = computeCorrectionStates(page.getContent());
        return page.map(da -> DailyAttendanceResDto.fromEntity(
                da, stateMap.getOrDefault(da.getDailyAttendanceId(), CorrectionState.NORMAL)));
    }

    /**
     * 회사 전체 월간 근태 조회 (관리자가 월간 리포트 확인)
     * - 100명 × 22일 = 2,200건/월 → 페이징 필수
     * - daily_attendance만 조회
     */
    @Transactional(readOnly = true)
    public Page<DailyAttendanceResDto> findMonthlyByCompany(UUID companyId, LocalDate from, LocalDate to, Pageable pageable) {
        Page<DailyAttendance> page = dailyAttendanceRepository
                .findMonthlyByCompany(companyId, from, to, pageable);
        Map<UUID, CorrectionState> stateMap = computeCorrectionStates(page.getContent());
        return page.map(da -> DailyAttendanceResDto.fromEntity(
                da, stateMap.getOrDefault(da.getDailyAttendanceId(), CorrectionState.NORMAL)));
    }

    /**
     * 페이지의 모든 DA 에 대한 CorrectionState 일괄 계산
     *  PENDING    closureStatus = UNDER_REVIEW
     *  COMPLETED  ADMIN_MANUAL 정정 로그 중 isCorrectedYn=Y + correctedBy not null 1건이라도 있음
     *  ABNORMAL   status = NORMAL + 과거 일자 + clock 한쪽 이상 누락
     *  NORMAL     그 외
     */
    private Map<UUID, CorrectionState> computeCorrectionStates(List<DailyAttendance> page) {
        Map<UUID, CorrectionState> result = new HashMap<>();
        if (page == null || page.isEmpty()) return result;

        // ADMIN_MANUAL 로그를 가진 DA 중 "관리자 승인된 정정" 보유 daId 모음
        Set<UUID> daIds = new HashSet<>();
        for (DailyAttendance da : page) daIds.add(da.getDailyAttendanceId());

        List<AttendanceLog> adminManual = attendanceLogRepository
                .findAdminManualByDailyAttendanceIds(daIds);
        Set<UUID> completedDaIds = new HashSet<>();
        for (AttendanceLog log : adminManual) {
            if ("Y".equals(log.getIsCorrectedYn()) && log.getCorrectedBy() != null) {
                completedDaIds.add(log.getDailyAttendance().getDailyAttendanceId());
            }
        }

        LocalDate today = LocalDate.now();
        for (DailyAttendance da : page) {
            UUID daId = da.getDailyAttendanceId();
            if (da.getClosureStatus() == ClosureStatus.UNDER_REVIEW) {
                result.put(daId, CorrectionState.PENDING);
            } else if (completedDaIds.contains(daId)) {
                result.put(daId, CorrectionState.COMPLETED);
            } else if (isAbnormal(da, today)) {
                result.put(daId, CorrectionState.ABNORMAL);
            } else {
                result.put(daId, CorrectionState.NORMAL);
            }
        }
        return result;
    }

    /** 과거 일자 + 정상 출근 상태인데 clock 한 쪽 이상 누락 = 이상 */
    private boolean isAbnormal(DailyAttendance da, LocalDate today) {
        if (da.getAttendanceDate() == null) return false;
        if (da.getAttendanceDate().isAfter(today.minusDays(1))) return false;  // 미래·오늘 제외
        if (da.getStatus() != AttendanceStatus.NORMAL) return false;            // 휴가·결근 제외
        return da.getFirstClockIn() == null || da.getLastClockOut() == null;
    }

    /**
     * 해당 날짜에 유효한 스케줄 기준 근무시간(분) 조회
     */
    @Transactional(readOnly = true)
    public int getScheduleWorkMinutes(UUID companyId, UUID memberId, LocalDate date){
        List<WorkSchedule> workSchedules = workScheduleRepository
                .findActiveSchedules(companyId, memberId, date);

        if (workSchedules.isEmpty()) {
            // 스케줄 미등록 시 근로기준법 기준 8시간(480분) 적용
            return 480;
        }

        // 첫 번째 = 최우선 스케줄 (개인 > 회사기본).
        // workMinutes 가 null 인 스케줄(레거시 행)도 안전하게 처리, 480분
        Integer mins = workSchedules.get(0).getWorkMinutes();
        return mins != null ? mins : 480;
    }

    /**
     * 근무시간 + 휴게시간 한 번에 조회 - 퇴근 처리 시 recalculate 호출용
     * 스케줄 자체가 없으면 480분 일한걸로
     */
    @Transactional(readOnly = true)
    public WorkBreakMinutes getScheduleWorkAndBreak(UUID companyId, UUID memberId, LocalDate date){
        List<WorkSchedule> workSchedules = workScheduleRepository
                .findActiveSchedules(companyId, memberId, date);
        if (workSchedules.isEmpty()) {
            return new WorkBreakMinutes(480, 0);
        }
        WorkSchedule schedule = workSchedules.get(0);
        int workMinutes = schedule.getWorkMinutes() != null ? schedule.getWorkMinutes() : 480;

        if (schedule.getWorkType() == WorkType.FIXED) {
            return new WorkBreakMinutes(workMinutes, schedule.computeBreakMinutes());
        }

        // FLEXIBLE — 직원이 선택한 점심시간 사용
        String yearMonth = date.format(YM);
        int memberBreakMin = memberScheduleSelectionRepository
                .findCurrentActive(memberId, yearMonth)
                .map(MemberScheduleSelection::computeBreakMinutes)
                .orElse(0);
        return new WorkBreakMinutes(workMinutes, memberBreakMin);
    }

    /**
     * 출근 시점에 유효한 스케줄 결정
     */
    private UUID resolveWorkScheduleId(UUID companyId, UUID memberId, LocalDate date) {
        List<WorkSchedule> schedules = workScheduleRepository
                .findActiveSchedules(companyId, memberId, date);

        if (schedules.isEmpty()) {
            return null;
        }
        return schedules.get(0).getWorkScheduleId();
    }
}
