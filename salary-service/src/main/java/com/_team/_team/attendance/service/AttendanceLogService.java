package com._team._team.attendance.service;


import com._team._team.attendance.domain.enums.*;
import com._team._team.attendance.repository.AttendanceLogRepository;
import com._team._team.attendance.domain.AttendanceLog;
import com._team._team.attendance.domain.DailyAttendance;
import com._team._team.attendance.dto.reqDto.AttendanceLogCreateReqDto;
import com._team._team.attendance.dto.resDto.AttendanceLogResDto;
import com._team._team.attendance.dto.resDto.DailyAttendanceResDto;
import com._team._team.attendance.repository.LeaveRequestRepository;
import com._team._team.dto.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * 출퇴근 이벤트 처리 서비스
 */
@Service
@Transactional
public class AttendanceLogService {

    private final AttendanceLogRepository attendanceLogRepository;
    private final DailyAttendanceService dailyAttendanceService;
    private final LeaveRequestRepository leaveRequestRepository;

    @Autowired
    public AttendanceLogService(AttendanceLogRepository attendanceLogRepository, DailyAttendanceService dailyAttendanceService, LeaveRequestRepository leaveRequestRepository) {
        this.attendanceLogRepository = attendanceLogRepository;
        this.dailyAttendanceService = dailyAttendanceService;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    /**
     * 출근 처리 (CLOCK_IN)
     */
    public DailyAttendanceResDto clockIn(UUID companyId, UUID memberId, AttendanceLogCreateReqDto reqDto){
        // 출근 시간 결정 (서버 시간)
        LocalDateTime eventTime = resolveEventTime(reqDto.getEventTime());
        LocalDate today = eventTime.toLocalDate();

        // 노무 수령 거부 가드 회사가 강제 지정한 연차일이면 출근 차단
        boolean designatedToday = leaveRequestRepository.existsForcedDesignationOnDate(
                memberId, companyId, today,
                LeaveInitiator.ADMIN_DESIGNATION,
                LeaveApprovalStatus.APPROVED);
        if (designatedToday) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "오늘은 회사가 지정한 연차일입니다 노무 수령을 거부합니다");
        }

        // 당일 DailyAttendance 조회 or 생성
        DailyAttendance dailyAttendance = dailyAttendanceService.findOrCreateDaily(companyId, memberId, today);

        // 중복 출근 체크
        if(attendanceLogRepository.existsByDailyAttendanceAndEventType(dailyAttendance, EventType.CLOCK_IN)){
            throw new BusinessException(HttpStatus.CONFLICT, "이미 출근 처리되었습니다.");
        }

        // 출근 로그 저장
        AttendanceLog log = reqDto.toEntity(dailyAttendance, EventType.CLOCK_IN, eventTime);
        attendanceLogRepository.save(log);

        // DailyAttendance 갱신
        // firstClockIn: 출근 시간 기록
        // status: ABSENT → NORMAL로 변경
        dailyAttendance.updateClockIn(eventTime);
        dailyAttendance.updateStatus(AttendanceStatus.NORMAL);

        return DailyAttendanceResDto.fromEntity(dailyAttendance);
    }

    /**
     * 퇴근 처리 (CLOCK_OUT)
     */
    public DailyAttendanceResDto clockOut(UUID companyId, UUID memberId, AttendanceLogCreateReqDto reqDto){
        // 퇴근 시간 결정
        LocalDateTime eventTime = resolveEventTime(reqDto.getEventTime());
        LocalDate today = eventTime.toLocalDate();

        // 당일 DailyAttendance 조회 (없으면 400 - 출근 기록 없음)
        DailyAttendance dailyAttendance = dailyAttendanceService.findDaily(companyId, memberId, today);

        // 출근 로그 존재 확인
        if(!attendanceLogRepository.existsByDailyAttendanceAndEventType(dailyAttendance, EventType.CLOCK_IN)){
            throw new BusinessException(HttpStatus.BAD_REQUEST, "출근 기록이 없습니다. 출근 처리를 먼저 해주세요.");
        }

        // 중복 퇴근 체크
        if(attendanceLogRepository.existsByDailyAttendanceAndEventType(dailyAttendance, EventType.CLOCK_OUT)){
            throw new BusinessException(HttpStatus.CONFLICT, "이미 퇴근 처리되었습니다.");
        }

        // 퇴근 로그 저장
        AttendanceLog log = reqDto.toEntity(dailyAttendance, EventType.CLOCK_OUT, eventTime);
        attendanceLogRepository.save(log);

        // DailyAttendance 갱신 + 근무시간/초과근무 계산
        // 1. 퇴근 시간 기록
        dailyAttendance.updateClockOut(eventTime);

        // 2. 이 사원의 기준 근무 + 휴게시간 조회
        DailyAttendanceService.WorkBreakMinutes wb =
                dailyAttendanceService.getScheduleWorkAndBreak(companyId, memberId, today);

        // 3. 근무시간 재계산
        // workedMinutes = (퇴근 - 출근) - 휴게(점심)
        // overtimeMinutes = workedMinutes 가 기준 초과 시 초과분
        dailyAttendance.recalculateMinutes(wb.workMinutes(), wb.breakMinutes());

        return DailyAttendanceResDto.fromEntity(dailyAttendance);
    }

    /**
     * 퇴근 취소 (CLOCK_OUT 되돌리기)
     * 직원이 잘못 퇴근 누른 경우에 사용
     */
    public DailyAttendanceResDto cancelClockOut(UUID companyId, UUID memberId) {
        LocalDate today = LocalDate.now();

        DailyAttendance da = dailyAttendanceService.findDaily(companyId, memberId, today);

        if (da.getClosureStatus() != ClosureStatus.OPEN) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "이미 마감된 근태는 취소할 수 없습니다. 정정 신청을 이용하세요.");
        }

        AttendanceLog clockOutLog = attendanceLogRepository
                .findTop1ByDailyAttendanceAndEventTypeOrderByEventTimeDesc(da, EventType.CLOCK_OUT)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST,
                        "퇴근 기록이 없습니다."));

        attendanceLogRepository.delete(clockOutLog);
        da.cancelClockOut();

        return DailyAttendanceResDto.fromEntity(da);
    }

    /**
     * 특정 날짜의 출퇴근 로그 목록 조회 (시간순)
     */
    @Transactional(readOnly = true)
    public List<AttendanceLogResDto> findLogs(UUID companyId, UUID memberId, LocalDate date){

        DailyAttendance dailyAttendance = dailyAttendanceService.findDaily(companyId, memberId, date);

        // daily_attendance_id로 해당일 로그 전체 조회 (시간순)
        List<AttendanceLog> logs = attendanceLogRepository
                .findByDailyAttendanceDailyAttendanceIdOrderByEventTime(dailyAttendance.getDailyAttendanceId());

        return logs.stream()
                .map(AttendanceLogResDto::fromEntity)
                .toList();
    }

    /**
     * 이벤트 시간 결정 - JVM TZ 무관 항상 UTC LocalDateTime 으로 저장
     * 프론트 dayjs.utc(iso).tz('Asia/Seoul') 변환으로 KST 표시
     */
    private LocalDateTime resolveEventTime(LocalDateTime eventTime) {
        return eventTime != null ? eventTime
                : LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
    }
}
