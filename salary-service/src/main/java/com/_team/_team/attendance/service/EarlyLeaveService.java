package com._team._team.attendance.service;

import com._team._team.attendance.domain.AttendanceLog;
import com._team._team.attendance.domain.DailyAttendance;
import com._team._team.attendance.domain.enums.AttendanceStatus;
import com._team._team.attendance.domain.enums.ClosureStatus;
import com._team._team.attendance.domain.enums.EventType;
import com._team._team.attendance.domain.enums.SourceType;
import com._team._team.attendance.repository.AttendanceLogRepository;
import com._team._team.attendance.repository.DailyAttendanceRepository;
import com._team._team.attendance.repository.LeaveRequestRepository;
import com._team._team.attendance.domain.LeaveRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * 조퇴계 결재 통합 서비스, 정책: 휴가 차감 X, 정규 근무 시간 줄이지 X
 * 7일 이내(+ 본인 승인 휴가 일수) 신청만 허용
 */
@Slf4j
@Service
public class EarlyLeaveService {

    /** 조퇴계 신청 가능 기간 (오늘 기준 N일 이내) */
    private static final int EARLY_LEAVE_WINDOW_DAYS = 7;

    private final DailyAttendanceRepository dailyAttendanceRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveRequestService leaveRequestService;
    private final DailyAttendanceCloseService dailyAttendanceCloseService;

    @Autowired
    public EarlyLeaveService(DailyAttendanceRepository dailyAttendanceRepository,
                             AttendanceLogRepository attendanceLogRepository,
                             LeaveRequestRepository leaveRequestRepository,
                             LeaveRequestService leaveRequestService,
                             DailyAttendanceCloseService dailyAttendanceCloseService) {
        this.dailyAttendanceRepository = dailyAttendanceRepository;
        this.attendanceLogRepository = attendanceLogRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveRequestService = leaveRequestService;
        this.dailyAttendanceCloseService = dailyAttendanceCloseService;
    }

    /**
     * 결재 상신 시 격리 (UNDER_REVIEW)
     */
    @Transactional
    public void submitEarlyLeave(UUID companyId, UUID memberId, UUID requestId,
                                 LocalDate attendanceDate,
                                 LocalDateTime earlyLeaveAt,
                                 String reason) {
        if (attendanceDate == null) {
            log.warn("[EarlyLeave] submit 일자 누락 memberId={}", memberId);
            return;
        }

        // 7일 + 휴가 일수 윈도우 검증
        LocalDate today = LocalDate.now();
        if (attendanceDate.isAfter(today)) {
            log.warn("[EarlyLeave] submit 미래 일자 차단 memberId={} date={}", memberId, attendanceDate);
            return;
        }
        long daysSince = ChronoUnit.DAYS.between(attendanceDate, today);
        long leaveDays = countApprovedLeaveDays(memberId, attendanceDate, today);
        long allowed = EARLY_LEAVE_WINDOW_DAYS + leaveDays;
        if (daysSince > allowed) {
            log.warn("[EarlyLeave] submit 윈도우 초과 memberId={} date={} daysSince={} allowed={}",
                    memberId, attendanceDate, daysSince, allowed);
            return;
        }

        // DA upsert
        DailyAttendance da = dailyAttendanceRepository
                .findByCompanyIdAndMemberIdAndAttendanceDate(companyId, memberId, attendanceDate)
                .orElseGet(() -> dailyAttendanceRepository.save(
                        DailyAttendance.builder()
                                .memberId(memberId)
                                .companyId(companyId)
                                .attendanceDate(attendanceDate)
                                .status(AttendanceStatus.NORMAL)
                                .closureStatus(ClosureStatus.OPEN)
                                .build()));

        if (da.getClosureStatus() == ClosureStatus.LOCKED) {
            log.warn("[EarlyLeave] submit LOCKED 차단 memberId={} date={}", memberId, attendanceDate);
            return;
        }

        // 조퇴 시각 가정 - 일단 lastClockOut 을 earlyLeaveAt 으로 임시 세팅 (승인되면 schedule.endTime 으로 보정)
        if (earlyLeaveAt != null) {
            upsertCorrectionLog(da, EventType.CLOCK_OUT, earlyLeaveAt, reason);
            da.updateClockOut(earlyLeaveAt);
        }

        if (da.getClosureStatus() != ClosureStatus.UNDER_REVIEW) {
            da.transitionClosure(ClosureStatus.UNDER_REVIEW);
        }

        log.info("[EarlyLeave] submit 격리 완료 memberId={} date={} requestId={}",
                memberId, attendanceDate, requestId);
    }

    /**
     * 결재 승인 적용 - 출/퇴근 시각은 실제 조퇴 시각 그대로 보존(이력/사용자 가시성 유지),
     * 조퇴했으면 WorkTimeClassifier 가 정규 근무 시간을 스케줄시간대로 근무시간을 만듬
     * 정책: 휴가 차감 X, 정규근무 줄이지 X (급여 정산만 정규 8h 유지)
     */
    @Transactional
    public void applyApprovedEarlyLeave(UUID companyId, UUID memberId, UUID approverId,
                                        LocalDate attendanceDate,
                                        LocalDateTime earlyLeaveAt,
                                        String reason) {
        if (attendanceDate == null) {
            log.warn("[EarlyLeave] approved 일자 누락 memberId={}", memberId);
            return;
        }

        DailyAttendance da = dailyAttendanceRepository
                .findByCompanyIdAndMemberIdAndAttendanceDate(companyId, memberId, attendanceDate)
                .orElse(null);

        if (da == null || da.getClosureStatus() != ClosureStatus.UNDER_REVIEW) {
            log.warn("[EarlyLeave] approved but DA 격리 안 된 상태 - 적용 스킵 memberId={} date={} status={}",
                    memberId, attendanceDate, da == null ? "NULL" : da.getClosureStatus());
            return;
        }

        // 조퇴 체크 - 출퇴근 시각은 실제 그대로, 정규 근무는 WorkTimeClassifier 가 스케줄 정규 근무 시간으로 만듬
        da.markEarlyLeaveExcused();

        // 정정 로그 markCorrected (사유 보존, 출퇴근 시각 변경 없음)
        attendanceLogRepository
                .findTop1ByDailyAttendanceAndEventTypeAndSourceTypeOrderByEventTimeDesc(
                        da, EventType.CLOCK_OUT, SourceType.ADMIN_MANUAL)
                .ifPresent(l -> l.markCorrected(approverId, l.getCorrectionReason()));

        // 재계산 + FINALIZED (조회했으면 반영해서 정규근무 시간 8h 처리)
        dailyAttendanceCloseService.recalculateAndFinalize(da.getDailyAttendanceId());

        log.info("[EarlyLeave] 결재 승인 적용 완료 memberId={} date={} earlyLeaveAt={}",
                memberId, attendanceDate, earlyLeaveAt);
    }

    /**
     * 결재 반려/취소 시 OPEN 복구 + 정정 로그 삭제 + 원본 시각 복구
     */
    @Transactional
    public void cancelSubmittedEarlyLeave(UUID companyId, UUID memberId,
                                          LocalDate attendanceDate) {
        if (attendanceDate == null) return;

        DailyAttendance da = dailyAttendanceRepository
                .findByCompanyIdAndMemberIdAndAttendanceDate(companyId, memberId, attendanceDate)
                .orElse(null);
        if (da == null) return;

        if (da.getClosureStatus() != ClosureStatus.UNDER_REVIEW) {
            log.info("[EarlyLeave] cancel skip - 상태={} memberId={} date={}",
                    da.getClosureStatus(), memberId, attendanceDate);
            return;
        }

        // 정정 로그(ADMIN_MANUAL) 삭제 - 조퇴는 퇴근 시각만 건드림
        attendanceLogRepository
                .findTop1ByDailyAttendanceAndEventTypeAndSourceTypeOrderByEventTimeDesc(
                        da, EventType.CLOCK_OUT, SourceType.ADMIN_MANUAL)
                .ifPresent(attendanceLogRepository::delete);

        // 원본 퇴근 시각 복구 (WEB/MOBILE/READER)
        LocalDateTime origOut = attendanceLogRepository
                .findTop1ByDailyAttendanceAndEventTypeAndSourceTypeNotOrderByEventTimeDesc(
                        da, EventType.CLOCK_OUT, SourceType.ADMIN_MANUAL)
                .map(AttendanceLog::getEventTime).orElse(null);
        da.updateClockOut(origOut);

        // 조퇴 반려/취소 체크
        da.clearEarlyLeaveExcused();

        da.transitionClosure(ClosureStatus.OPEN);

        log.info("[EarlyLeave] 반려/취소 복구 완료 memberId={} date={}", memberId, attendanceDate);
    }

    /** 정정 채널(ADMIN_MANUAL) 로그를 1건씩만 유지 - 재신청 시 갱신 */
    private void upsertCorrectionLog(DailyAttendance da, EventType type, LocalDateTime when, String reason) {
        attendanceLogRepository
                .findTop1ByDailyAttendanceAndEventTypeAndSourceTypeOrderByEventTimeDesc(
                        da, type, SourceType.ADMIN_MANUAL)
                .ifPresent(attendanceLogRepository::delete);

        AttendanceLog log = AttendanceLog.builder()
                .dailyAttendance(da)
                .memberId(da.getMemberId())
                .eventType(type)
                .eventTime(when)
                .sourceType(SourceType.ADMIN_MANUAL)
                .isCorrectedYn("Y")
                .correctionReason(reason)
                .build();
        attendanceLogRepository.save(log);
    }

    /** [from, to] 와 겹치는 본인의 승인 휴가 일수 합계
     *  비연속 휴가계획이 있는 휴가신청은 [from, to] 안에 들어간 plannedDates 개수만 합산
     */
    private long countApprovedLeaveDays(UUID memberId, LocalDate from, LocalDate to) {
        List<LeaveRequest> overlapping = leaveRequestRepository
                .findApprovedOverlapping(memberId, from, to);
        long total = 0;
        for (LeaveRequest l : overlapping) {
            java.util.Set<LocalDate> planned = leaveRequestService.parsePlannedDates(l);
            if (!planned.isEmpty()) {
                total += planned.stream()
                        .filter(d -> !d.isBefore(from) && !d.isAfter(to))
                        .count();
                continue;
            }
            LocalDate s = l.getStartDate().isBefore(from) ? from : l.getStartDate();
            LocalDate e = l.getEndDate().isAfter(to) ? to : l.getEndDate();
            if (!s.isAfter(e)) {
                total += ChronoUnit.DAYS.between(s, e) + 1;
            }
        }
        return total;
    }
}
