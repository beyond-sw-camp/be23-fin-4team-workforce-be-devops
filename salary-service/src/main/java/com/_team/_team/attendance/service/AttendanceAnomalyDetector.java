package com._team._team.attendance.service;

import com._team._team.attendance.repository.CompanyHolidayRepository;
import com._team._team.attendance.repository.CompanyLeaveTypeRepository;
import com._team._team.attendance.repository.DailyAttendanceRepository;
import com._team._team.attendance.repository.WorkScheduleRepository;
import com._team._team.attendance.domain.CompanyLeaveType;
import com._team._team.attendance.domain.DailyAttendance;
import com._team._team.attendance.domain.LeaveRequest;
import com._team._team.attendance.domain.WorkSchedule;
import com._team._team.attendance.domain.enums.AttendanceStatus;
import com._team._team.dto.NotificationMessage;
import com._team._team.notification.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 근태 이상 감지기
// 지각/조퇴/결근의심/미퇴근/휴가일출근을 뽑아서 본인한테 알림
@Slf4j
@Component
public class AttendanceAnomalyDetector {

    // 허용오차(분) - 이 이내로 들어오면 정상 취급
    private static final int GRACE_MINUTES = 10;

    private final DailyAttendanceRepository dailyAttendanceRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final CompanyHolidayRepository companyHolidayRepository;
    private final LeaveRequestService leaveRequestService;
    private final CompanyLeaveTypeRepository companyLeaveTypeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public AttendanceAnomalyDetector(DailyAttendanceRepository dailyAttendanceRepository,
                                     WorkScheduleRepository workScheduleRepository,
                                     CompanyHolidayRepository companyHolidayRepository,
                                     LeaveRequestService leaveRequestService,
                                     CompanyLeaveTypeRepository companyLeaveTypeRepository,
                                     ApplicationEventPublisher eventPublisher) {
        this.dailyAttendanceRepository = dailyAttendanceRepository;
        this.workScheduleRepository = workScheduleRepository;
        this.companyHolidayRepository = companyHolidayRepository;
        this.leaveRequestService = leaveRequestService;
        this.companyLeaveTypeRepository = companyLeaveTypeRepository;
        this.eventPublisher = eventPublisher;
    }

    // 대상일 근태를 확인해서 이상 건마다 NotificationMessage 이벤트 발행
    @Transactional(readOnly = true)
    public AnomalyResult detect(LocalDate targetDate) {
        List<DailyAttendance> all = dailyAttendanceRepository.findAllByAttendanceDate(targetDate);

        int late = 0, early = 0, missing = 0, absent = 0, leaveIn = 0;

        for (DailyAttendance da : all) {
            AttendanceStatus status = da.getStatus();

            // 전일 휴가(LEAVE) 중 출근 기록, HALF 는 반차라 출근이 정상이므로 제외
            if (status == AttendanceStatus.LEAVE && da.getFirstClockIn() != null) {
                publish(da, NotificationType.ATTENDANCE_LEAVE_CLOCKIN,
                        "휴가일에 출근 기록이 있습니다. (" + targetDate + ")");
                leaveIn++;
                continue;
            }

            // 결근(ABSENT) 및 전일 휴가(LEAVE) 는 상태 확정이라 이후 판정 제외
            if (status == AttendanceStatus.ABSENT || status == AttendanceStatus.LEAVE) continue;

            WorkSchedule schedule = findSchedule(da.getCompanyId(), da.getMemberId(), targetDate);

            // 결근 의심, 평일이고 공휴일 아니고 스케줄은 있고 clockIn 없음 (NORMAL 만 대상, HALF 는 반차라 제외)
            if (da.getFirstClockIn() == null) {
                if (status == AttendanceStatus.NORMAL
                        && isWorkday(da.getCompanyId(), targetDate) && schedule != null) {
                    publish(da, NotificationType.ATTENDANCE_ABSENT_SUSPECT,
                            "출근 기록이 없습니다. 출근 상태를 확인해주세요. (" + targetDate + ")");
                    absent++;
                }
                continue;
            }

            // 미퇴근, clockIn 있는데 clockOut 없음 (NORMAL/HALF 공통)
            if (da.getLastClockOut() == null) {
                publish(da, NotificationType.ATTENDANCE_MISSING_CLOCK_OUT,
                        "퇴근 기록이 없습니다. 퇴근 처리해주세요. (" + targetDate + ")");
                missing++;
                continue;
            }

            // 지각/조퇴는 스케줄 있어야 판정 가능
            if (schedule == null) continue;

            /*
             * FLEXIBLE 스케줄은 startTime/endTime/workMinutes 가 null
             */
            if (schedule.getStartTime() == null
                    || schedule.getEndTime() == null
                    || schedule.getWorkMinutes() == null) continue;

            // 반차인 경우 스케줄을 반쪽만 기준으로 재계산
            LocalTime effectiveStart = schedule.getStartTime();
            LocalTime effectiveEnd = schedule.getEndTime();
            if (status == AttendanceStatus.HALF) {
                HalfDayShift shift = resolveHalfDayShift(
                        da.getCompanyId(), da.getMemberId(), targetDate);
                if (shift == HalfDayShift.AM) {
                    effectiveStart = calculateHalfDayCutoff(schedule);
                } else if (shift == HalfDayShift.PM) {
                    effectiveEnd = calculateHalfDayCutoff(schedule);
                } else {
                    // 반차 유형 불명, 판정 보류
                    continue;
                }
            }

            LocalDateTime scheduledStart = targetDate.atTime(effectiveStart);
            LocalDateTime scheduledEnd = targetDate.atTime(effectiveEnd);

            long lateMin = Duration.between(scheduledStart, da.getFirstClockIn()).toMinutes();
            if (lateMin > GRACE_MINUTES) {
                publish(da, NotificationType.ATTENDANCE_LATE,
                        "지각이 감지되었습니다. (" + lateMin + "분 지각)");
                late++;
            }

            long earlyMin = Duration.between(da.getLastClockOut(), scheduledEnd).toMinutes();
            if (earlyMin > GRACE_MINUTES) {
                publish(da, NotificationType.ATTENDANCE_EARLY_LEAVE,
                        "조퇴가 감지되었습니다. (" + earlyMin + "분 조퇴)");
                early++;
            }
        }

        log.info("[ANOMALY] targetDate={} 지각={} 조퇴={} 미퇴근={} 결근의심={} 휴가출근={}",
                targetDate, late, early, missing, absent, leaveIn);
        return new AnomalyResult(late, early, missing, absent, leaveIn);
    }

    // 해당일 유효 스케줄 1건 (개인 우선, 없으면 회사 기본) 없으면 null
    private WorkSchedule findSchedule(UUID companyId, UUID memberId, LocalDate date) {
        List<WorkSchedule> list = workScheduleRepository.findActiveSchedules(companyId, memberId, date);
        return list.isEmpty() ? null : list.get(0);
    }

    // 주말, 회사 공휴일이면 결근 판정에서 제외
    private boolean isWorkday(UUID companyId, LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return !companyHolidayRepository.existsByCompanyIdAndHolidayDate(companyId, date);
    }

    // 본인한테 알림 senderId는 시스템 발송이라 null
    private void publish(DailyAttendance da, NotificationType type, String content) {
        eventPublisher.publishEvent(NotificationMessage.builder()
                .receiverId(da.getMemberId())
                .senderId(null)
                .notificationType(type)
                .content(content)
                .targetId(da.getDailyAttendanceId())
                .targetType("DAILY_ATTENDANCE")
                .build());
    }

    // 반차 유형, CompanyLeaveType.code 가 HALF_AM 이면 오전, HALF_PM 이면 오후
    private enum HalfDayShift { AM, PM, UNKNOWN }

    // 본인 해당 날짜 반차 유형 조회, LeaveRequest -> CompanyLeaveType.code 연결
    private HalfDayShift resolveHalfDayShift(UUID companyId, UUID memberId, LocalDate date) {
        Optional<LeaveRequest> leave = leaveRequestService
                .findActiveOnDate(companyId, memberId, date);
        if (leave.isEmpty()) {
            return HalfDayShift.UNKNOWN;
        }
        Optional<CompanyLeaveType> type = companyLeaveTypeRepository
                .findByCompanyLeaveTypeIdAndCompanyIdAndDelYn(
                        leave.get().getCompanyLeaveTypeId(), companyId, "N");
        if (type.isEmpty() || type.get().getCode() == null) {
            return HalfDayShift.UNKNOWN;
        }
        String code = type.get().getCode();
        if ("HALF_AM".equalsIgnoreCase(code)) return HalfDayShift.AM;
        if ("HALF_PM".equalsIgnoreCase(code)) return HalfDayShift.PM;
        return HalfDayShift.UNKNOWN;
    }

    // 근무 시작~종료의 중간점, 반차 경계 시각
    private LocalTime calculateHalfDayCutoff(WorkSchedule schedule) {
        return schedule.getStartTime().plusMinutes(schedule.getWorkMinutes() / 2);
    }

    public record AnomalyResult(int lateCount, int earlyLeaveCount,
                                int missingClockOutCount, int absentSuspectCount,
                                int leaveClockInCount) {}
}
