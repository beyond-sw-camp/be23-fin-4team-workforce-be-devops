package com._team._team.attendance.service;

import com._team._team.attendance.domain.AttendanceLog;
import com._team._team.attendance.domain.DailyAttendance;
import com._team._team.attendance.domain.LeaveRequest;
import com._team._team.attendance.domain.enums.AttendanceStatus;
import com._team._team.attendance.domain.enums.ClosureStatus;
import com._team._team.attendance.domain.enums.EventType;
import com._team._team.attendance.domain.enums.SourceType;
import com._team._team.attendance.dto.reqDto.AttendanceCorrectionReqDto;
import com._team._team.attendance.dto.resDto.AttendanceCorrectionPendingResDto;
import com._team._team.attendance.dto.resDto.MissingAttendanceSuspectResDto;
import com._team._team.attendance.repository.AttendanceLogRepository;
import com._team._team.attendance.repository.CompanyHolidayRepository;
import com._team._team.attendance.repository.DailyAttendanceRepository;
import com._team._team.attendance.repository.LeaveRequestRepository;
import com._team._team.dto.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 출퇴근 정정 신청, 승인, 반려 서비스
 * 배치와의 관계:
 *  - 02:00 DRAFT 배치는 OPEN 만, 14:00 FINAL 배치는 DRAFT 만 처리
 *  - 관리자가 확인하는 근태 상태(UNDER_REVIEW) 는 두 배치 모두 무시 -> 정정 중 기간 고려해서 무시
 */
@Slf4j
@Service
public class AttendanceCorrectionService {

    /** 정정 신청 가능 기간 (오늘 기준 N일 이내) */
    private static final int CORRECTION_WINDOW_DAYS = 7;

    private final DailyAttendanceRepository dailyAttendanceRepository;
    private final AttendanceLogRepository attendanceLogRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveRequestService leaveRequestService;
    private final CompanyHolidayRepository companyHolidayRepository;
    private final DailyAttendanceCloseService dailyAttendanceCloseService;

    @Autowired
    public AttendanceCorrectionService(
            DailyAttendanceRepository dailyAttendanceRepository,
            AttendanceLogRepository attendanceLogRepository,
            LeaveRequestRepository leaveRequestRepository,
            LeaveRequestService leaveRequestService,
            CompanyHolidayRepository companyHolidayRepository,
            DailyAttendanceCloseService dailyAttendanceCloseService) {
        this.dailyAttendanceRepository = dailyAttendanceRepository;
        this.attendanceLogRepository = attendanceLogRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveRequestService = leaveRequestService;
        this.companyHolidayRepository = companyHolidayRepository;
        this.dailyAttendanceCloseService = dailyAttendanceCloseService;
    }

    /**
     * 1. 직원: 정정 신청
     */
    @Transactional
    public UUID requestCorrection(UUID companyId, UUID memberId, AttendanceCorrectionReqDto req) {

        // 입력 검증 — 출근/퇴근 둘 다 비어있으면 의미 없음
        if (req.getRequestedClockIn() == null && req.getRequestedClockOut() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "정정 출근 시각 또는 퇴근 시각 중 하나 이상은 입력해야 합니다.");
        }

        LocalDate date = req.getAttendanceDate();
        LocalDate today = LocalDate.now();

        // 미래 일자 차단
        if (date.isAfter(today)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "미래 일자는 정정할 수 없습니다.");
        }

        // 7일 정책 + 휴가 일수 자동 가산 (휴가, 휴직 다녀온 직원 안전망)
        long daysSince = ChronoUnit.DAYS.between(date, today);
        long leaveDays = countApprovedLeaveDays(memberId, date, today);
        long allowed = CORRECTION_WINDOW_DAYS + leaveDays;
        if (daysSince > allowed) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    String.format(
                            "정정 신청은 최근 %d일 (+ 본인 승인 휴가 %d일) 이내만 가능합니다. (요청: %d일 전)",
                            CORRECTION_WINDOW_DAYS, leaveDays, daysSince));
        }

        // 시각 일관성 , 둘 다 들어왔으면 in <= out
        if (req.getRequestedClockIn() != null && req.getRequestedClockOut() != null
                && req.getRequestedClockIn().isAfter(req.getRequestedClockOut())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "퇴근 시각은 출근 시각 이후여야 합니다.");
        }

        // DA upsert - 없으면 신규 생성 (status=NORMAL, closureStatus=OPEN)
        DailyAttendance da = dailyAttendanceRepository
                .findByCompanyIdAndMemberIdAndAttendanceDate(companyId, memberId, date)
                .orElseGet(() -> dailyAttendanceRepository.save(
                        DailyAttendance.builder()
                                .memberId(memberId)
                                .companyId(companyId)
                                .attendanceDate(date)
                                .status(AttendanceStatus.NORMAL)
                                .closureStatus(ClosureStatus.OPEN)
                                .build()));

        // LOCKED 차단 (월마감 후)
        if (da.getClosureStatus() == ClosureStatus.LOCKED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "월마감된 근태는 정정할 수 없습니다.");
        }

        // 출근 정정 - 기존 정정 로그 삭제 후 신규 추가, DA 시각 갱신
        if (req.getRequestedClockIn() != null) {
            upsertCorrectionLog(da, EventType.CLOCK_IN, req.getRequestedClockIn(), req.getReason());
            da.updateClockIn(req.getRequestedClockIn());
        }
        // 퇴근 정정 - 동일
        if (req.getRequestedClockOut() != null) {
            upsertCorrectionLog(da, EventType.CLOCK_OUT, req.getRequestedClockOut(), req.getReason());
            da.updateClockOut(req.getRequestedClockOut());
        }

        // 검토 대기 상태로 전이 - 02:00/14:00 배치 모두 무시
        da.transitionClosure(ClosureStatus.UNDER_REVIEW);

        log.info("[AttendanceCorrection] 신청 companyId={} memberId={} date={} in={} out={}",
                companyId, memberId, date, req.getRequestedClockIn(), req.getRequestedClockOut());

        return da.getDailyAttendanceId();
    }

    /**
     * [from, to] 와 겹치는 본인의 승인 휴가 일수 합계
     * 휴가 기간이 일부만 겹치면 겹친 부분만 카운트
     * 비연속 휴가계획이 있는 휴가는 [from, to] 안에 포함된 휴가 계획일(비연속) 개수만 합산
     */
    private long countApprovedLeaveDays(UUID memberId, LocalDate from, LocalDate to) {
        List<LeaveRequest> overlapping = leaveRequestRepository
                .findApprovedOverlapping(memberId, from, to);
        long total = 0;
        for (LeaveRequest l : overlapping) {
            Set<LocalDate> planned = leaveRequestService.parsePlannedDates(l);
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

    /** 정정 채널(ADMIN_MANUAL) 로그를 1건씩만 유지 — 재신청 시 갱신 */
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

    /**
     * 2. 관리자: 검토 큐 조회
     */
    @Transactional(readOnly = true)
    public List<AttendanceCorrectionPendingResDto> findPending(UUID companyId) {
        List<DailyAttendance> list = dailyAttendanceRepository
                .findAllByCompanyIdAndClosureStatusOrderByDateDesc(companyId, ClosureStatus.UNDER_REVIEW);

        List<AttendanceCorrectionPendingResDto> result = new ArrayList<>();
        for (DailyAttendance da : list) {
            // 가장 최근 정정 로그(둘 중 시간 늦은 것) 에서 사유/신청 시점 추출
            Optional<AttendanceLog> inLog = attendanceLogRepository
                    .findTop1ByDailyAttendanceAndEventTypeAndSourceTypeOrderByEventTimeDesc(
                            da, EventType.CLOCK_IN, SourceType.ADMIN_MANUAL);
            Optional<AttendanceLog> outLog = attendanceLogRepository
                    .findTop1ByDailyAttendanceAndEventTypeAndSourceTypeOrderByEventTimeDesc(
                            da, EventType.CLOCK_OUT, SourceType.ADMIN_MANUAL);

            AttendanceLog latest = pickLatest(inLog.orElse(null), outLog.orElse(null));
            String reason = latest != null ? latest.getCorrectionReason() : null;
            LocalDateTime requestedAt = latest != null ? latest.getCreatedAt() : null;

            result.add(AttendanceCorrectionPendingResDto.builder()
                    .dailyAttendanceId(da.getDailyAttendanceId())
                    .memberId(da.getMemberId())
                    .attendanceDate(da.getAttendanceDate())
                    .requestedClockIn(da.getFirstClockIn())
                    .requestedClockOut(da.getLastClockOut())
                    .reason(reason)
                    .requestedAt(requestedAt)
                    .build());
        }
        return result;
    }

    private AttendanceLog pickLatest(AttendanceLog a, AttendanceLog b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.getCreatedAt() != null && b.getCreatedAt() != null
                && a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b;
    }

    /**
     * 3. 관리자: 승인
     */
    @Transactional
    public void approve(UUID companyId, UUID dailyAttendanceId, UUID adminId) {
        DailyAttendance da = dailyAttendanceRepository.findById(dailyAttendanceId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "근태 기록을 찾을 수 없습니다."));

        if (!da.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "회사 불일치");
        }
        if (da.getClosureStatus() != ClosureStatus.UNDER_REVIEW) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "검토 대기 상태가 아닙니다. status=" + da.getClosureStatus());
        }

        // 정정 로그에 관리자 마킹 (correctedBy / correctedAt)
        attendanceLogRepository
                .findTop1ByDailyAttendanceAndEventTypeAndSourceTypeOrderByEventTimeDesc(
                        da, EventType.CLOCK_IN, SourceType.ADMIN_MANUAL)
                .ifPresent(l -> l.markCorrected(adminId, l.getCorrectionReason()));
        attendanceLogRepository
                .findTop1ByDailyAttendanceAndEventTypeAndSourceTypeOrderByEventTimeDesc(
                        da, EventType.CLOCK_OUT, SourceType.ADMIN_MANUAL)
                .ifPresent(l -> l.markCorrected(adminId, l.getCorrectionReason()));

        // 재계산 + FINALIZED 직행
        dailyAttendanceCloseService.recalculateAndFinalize(dailyAttendanceId);

        log.info("[AttendanceCorrection] 승인 daId={} adminId={}", dailyAttendanceId, adminId);
    }

    /*
     * 4. 관리자: 반려
     */
    @Transactional
    public void reject(UUID companyId, UUID dailyAttendanceId, UUID adminId, String rejectReason) {
        DailyAttendance da = dailyAttendanceRepository.findById(dailyAttendanceId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "근태 기록을 찾을 수 없습니다."));

        if (!da.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "회사 불일치");
        }
        if (da.getClosureStatus() != ClosureStatus.UNDER_REVIEW) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "검토 대기 상태가 아닙니다. status=" + da.getClosureStatus());
        }

        // 정정 로그 삭제 (CLOCK_IN, CLOCK_OUT)
        attendanceLogRepository
                .findTop1ByDailyAttendanceAndEventTypeAndSourceTypeOrderByEventTimeDesc(
                        da, EventType.CLOCK_IN, SourceType.ADMIN_MANUAL)
                .ifPresent(attendanceLogRepository::delete);
        attendanceLogRepository
                .findTop1ByDailyAttendanceAndEventTypeAndSourceTypeOrderByEventTimeDesc(
                        da, EventType.CLOCK_OUT, SourceType.ADMIN_MANUAL)
                .ifPresent(attendanceLogRepository::delete);

        // 직전 원본 로그(WEB/MOBILE/READER) 시각으로 firstClockIn / lastClockOut 복구
        LocalDateTime origIn = attendanceLogRepository
                .findTop1ByDailyAttendanceAndEventTypeAndSourceTypeNotOrderByEventTimeDesc(
                        da, EventType.CLOCK_IN, SourceType.ADMIN_MANUAL)
                .map(AttendanceLog::getEventTime).orElse(null);
        LocalDateTime origOut = attendanceLogRepository
                .findTop1ByDailyAttendanceAndEventTypeAndSourceTypeNotOrderByEventTimeDesc(
                        da, EventType.CLOCK_OUT, SourceType.ADMIN_MANUAL)
                .map(AttendanceLog::getEventTime).orElse(null);

        da.updateClockIn(origIn);
        da.updateClockOut(origOut);

        // 다음 DRAFT 배치가 자연스럽게 다시 잡도록 OPEN 으로 복귀
        da.transitionClosure(ClosureStatus.OPEN);

        log.info("[AttendanceCorrection] 반려 daId={} adminId={} reason={}",
                dailyAttendanceId, adminId, rejectReason);
    }

    /**
     * 결재 통합 정정 - 결재 올리는 시점에 일일근태 격리(UNDER_REVIEW)
     * 7일 + 본인 승인 휴가 일수 윈도우 검증, 위반 시 처리 안 함 (결재는 이미 상신됨, 승인 시 거부)
     */
    @Transactional
    public void submitCorrection(UUID companyId, UUID memberId, UUID requestId,
                                 LocalDate attendanceDate,
                                 LocalDateTime requestedClockIn,
                                 LocalDateTime requestedClockOut,
                                 String reason) {
        if (attendanceDate == null) {
            log.warn("[AttendanceCorrection] submit 정정 일자 누락, memberId={}", memberId);
            return;
        }

        // 7일 + 휴가 일수 윈도우 검증 - 위반 시 일일근태 격리 안 함
        LocalDate today = LocalDate.now();
        if (attendanceDate.isAfter(today)) {
            log.warn("[AttendanceCorrection] submit 미래 일자 차단 memberId={} date={}", memberId, attendanceDate);
            return;
        }
        long daysSince = ChronoUnit.DAYS.between(attendanceDate, today);
        long leaveDays = countApprovedLeaveDays(memberId, attendanceDate, today);
        long allowed = CORRECTION_WINDOW_DAYS + leaveDays;
        if (daysSince > allowed) {
            log.warn("[AttendanceCorrection] submit 윈도우 초과 memberId={} date={} daysSince={} allowed={}",
                    memberId, attendanceDate, daysSince, allowed);
            return;
        }

        // DA upsert - 없으면 신규 생성
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

        // LOCKED 차단 (월마감 후)
        if (da.getClosureStatus() == ClosureStatus.LOCKED) {
            log.warn("[AttendanceCorrection] submit LOCKED 차단 memberId={} date={}", memberId, attendanceDate);
            return;
        }

        // 출근 정정 - 기존 정정 로그 삭제 후 신규 추가, DA 시각 갱신
        if (requestedClockIn != null) {
            upsertCorrectionLog(da, EventType.CLOCK_IN, requestedClockIn, reason);
            da.updateClockIn(requestedClockIn);
        }
        if (requestedClockOut != null) {
            upsertCorrectionLog(da, EventType.CLOCK_OUT, requestedClockOut, reason);
            da.updateClockOut(requestedClockOut);
        }

        // UNDER_REVIEW 격리 - 02:00/14:00 배치 모두 무시
        if (da.getClosureStatus() != ClosureStatus.UNDER_REVIEW) {
            da.transitionClosure(ClosureStatus.UNDER_REVIEW);
        }

        log.info("[AttendanceCorrection] submit 격리 완료 memberId={} date={} requestId={}",
                memberId, attendanceDate, requestId);
    }

    /**
     * 결재 통합 정정 반려 - UNDER_REVIEW 일일근태를 OPEN 으로 복구, 정정 로그 삭제 + 원본 시각 복구
     */
    @Transactional
    public void cancelSubmittedCorrection(UUID companyId, UUID memberId,
                                          LocalDate attendanceDate) {
        if (attendanceDate == null) return;

        DailyAttendance da = dailyAttendanceRepository
                .findByCompanyIdAndMemberIdAndAttendanceDate(companyId, memberId, attendanceDate)
                .orElse(null);
        if (da == null) return;

        // UNDER_REVIEW 가 아니면 복구 대상 아님
        if (da.getClosureStatus() != ClosureStatus.UNDER_REVIEW) {
            log.info("[AttendanceCorrection] cancel skip - 상태={} memberId={} date={}",
                    da.getClosureStatus(), memberId, attendanceDate);
            return;
        }

        // 정정 로그(ADMIN_MANUAL) 삭제
        attendanceLogRepository
                .findTop1ByDailyAttendanceAndEventTypeAndSourceTypeOrderByEventTimeDesc(
                        da, EventType.CLOCK_IN, SourceType.ADMIN_MANUAL)
                .ifPresent(attendanceLogRepository::delete);
        attendanceLogRepository
                .findTop1ByDailyAttendanceAndEventTypeAndSourceTypeOrderByEventTimeDesc(
                        da, EventType.CLOCK_OUT, SourceType.ADMIN_MANUAL)
                .ifPresent(attendanceLogRepository::delete);

        // 원본 출/퇴근 시각 복구
        LocalDateTime origIn = attendanceLogRepository
                .findTop1ByDailyAttendanceAndEventTypeAndSourceTypeNotOrderByEventTimeDesc(
                        da, EventType.CLOCK_IN, SourceType.ADMIN_MANUAL)
                .map(AttendanceLog::getEventTime).orElse(null);
        LocalDateTime origOut = attendanceLogRepository
                .findTop1ByDailyAttendanceAndEventTypeAndSourceTypeNotOrderByEventTimeDesc(
                        da, EventType.CLOCK_OUT, SourceType.ADMIN_MANUAL)
                .map(AttendanceLog::getEventTime).orElse(null);

        da.updateClockIn(origIn);
        da.updateClockOut(origOut);

        // 다음 배치가 자연스럽게 다시 잡도록 OPEN 으로 복귀
        da.transitionClosure(ClosureStatus.OPEN);

        log.info("[AttendanceCorrection] 결재 반려/취소 복구 완료 memberId={} date={}", memberId, attendanceDate);
    }

    /**
     * 결재 통합 일일 근태 정정 승인
     */
    @Transactional
    public void applyApprovedCorrection(UUID companyId, UUID memberId, UUID approverId,
                                        LocalDate attendanceDate,
                                        LocalDateTime requestedClockIn,
                                        LocalDateTime requestedClockOut,
                                        String reason) {
        if (attendanceDate == null) {
            log.warn("[AttendanceCorrection] 정정 일자 누락, memberId={}", memberId);
            return;
        }

        DailyAttendance da = dailyAttendanceRepository
                .findByCompanyIdAndMemberIdAndAttendanceDate(companyId, memberId, attendanceDate)
                .orElse(null);

        // submit 시점에 윈도우 위반/LOCKED 등으로 격리 안 된 경우 - 결재 승인되어도 일일근태 적용 안 함
        if (da == null || da.getClosureStatus() != ClosureStatus.UNDER_REVIEW) {
            log.warn("[AttendanceCorrection] approved but DA 격리 안 된 상태 - 적용 스킵 memberId={} date={} status={}",
                    memberId, attendanceDate, da == null ? "NULL" : da.getClosureStatus());
            return;
        }

        // 정정 로그 markCorrected (correctedBy = approverId)
        attendanceLogRepository
                .findTop1ByDailyAttendanceAndEventTypeAndSourceTypeOrderByEventTimeDesc(
                        da, EventType.CLOCK_IN, SourceType.ADMIN_MANUAL)
                .ifPresent(l -> l.markCorrected(approverId, l.getCorrectionReason()));
        attendanceLogRepository
                .findTop1ByDailyAttendanceAndEventTypeAndSourceTypeOrderByEventTimeDesc(
                        da, EventType.CLOCK_OUT, SourceType.ADMIN_MANUAL)
                .ifPresent(l -> l.markCorrected(approverId, l.getCorrectionReason()));

        // 재계산 + FINALIZED (UNDER_REVIEW -> FINALIZED)
        dailyAttendanceCloseService.recalculateAndFinalize(da.getDailyAttendanceId());

        log.info("[AttendanceCorrection] 결재 승인 적용 완료 memberId={} date={}", memberId, attendanceDate);
    }

    /**
     * 5. 직원: 누락 후보 일자 (휴가, 휴직 복귀 안전망)
     *    정정 가능 윈도우 안에서 출퇴근이 누락된 영업일을 찾아 보여줌
     */
    @Transactional(readOnly = true)
    public List<MissingAttendanceSuspectResDto> findMissingSuspects(UUID companyId, UUID memberId) {
        LocalDate today = LocalDate.now();

        // 1. 휴가 일수 가산 - 정정 윈도우 결정 (충분히 긴 30일 안에서 카운트)
        long leaveDays = countApprovedLeaveDays(memberId, today.minusDays(30), today);
        LocalDate windowStart = today.minusDays(CORRECTION_WINDOW_DAYS + leaveDays);

        // 2. 본인 승인 휴가 기간 모음 (해당 일자 제외)
        // 비연속 휴가계획이 있는 휴가는 그 날짜만 leaveDates 에 포함
        List<LeaveRequest> myLeaves = leaveRequestRepository
                .findApprovedOverlapping(memberId, windowStart, today);
        Set<LocalDate> leaveDates = new HashSet<>();
        for (LeaveRequest l : myLeaves) {
            Set<LocalDate> planned = leaveRequestService.parsePlannedDates(l);
            if (!planned.isEmpty()) {
                planned.stream()
                        .filter(d -> !d.isBefore(windowStart) && !d.isAfter(today))
                        .forEach(leaveDates::add);
                continue;
            }
            LocalDate s = l.getStartDate().isBefore(windowStart) ? windowStart : l.getStartDate();
            LocalDate e = l.getEndDate().isAfter(today) ? today : l.getEndDate();
            for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
                leaveDates.add(d);
            }
        }

        // 3. 회사 공휴일 기간 모음 (해당 일자 제외)
        Set<LocalDate> holidayDates = companyHolidayRepository
                .findAllInRange(companyId, windowStart, today)
                .stream()
                .map(h -> h.getHolidayDate())
                .collect(Collectors.toSet());

        // 4. 일자별 매핑
        List<DailyAttendance> myDas = dailyAttendanceRepository
                .findAllByMemberInRange(memberId, windowStart, today);
        Map<LocalDate, DailyAttendance> daByDate = new HashMap<>();
        for (DailyAttendance da : myDas) {
            daByDate.put(da.getAttendanceDate(), da);
        }

        // 5. 영업일 순회 - 누락 후보 추출 (오늘은 진행중이라 제외)
        List<MissingAttendanceSuspectResDto> result = new ArrayList<>();
        for (LocalDate d = windowStart; d.isBefore(today); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) continue;
            if (holidayDates.contains(d)) continue;
            if (leaveDates.contains(d)) continue;

            DailyAttendance da = daByDate.get(d);
            if (da == null) {
                result.add(MissingAttendanceSuspectResDto.builder()
                        .date(d).reasonCode("NO_RECORD").build());
                continue;
            }
            // 검토중, 잠금 상태는 안내 대상 아님
            if (da.getClosureStatus() == ClosureStatus.UNDER_REVIEW
                    || da.getClosureStatus() == ClosureStatus.LOCKED) continue;
            // 휴가/반차 상태로 처리된 날도 제외
            if (da.getStatus() == AttendanceStatus.LEAVE
                    || da.getStatus() == AttendanceStatus.HALF) continue;

            if (da.getFirstClockIn() == null && da.getLastClockOut() == null) {
                result.add(MissingAttendanceSuspectResDto.builder()
                        .date(d).reasonCode("NO_RECORD").build());
            } else if (da.getFirstClockIn() == null) {
                result.add(MissingAttendanceSuspectResDto.builder()
                        .date(d).reasonCode("CLOCK_IN_MISSING").build());
            } else if (da.getLastClockOut() == null) {
                result.add(MissingAttendanceSuspectResDto.builder()
                        .date(d).reasonCode("CLOCK_OUT_MISSING").build());
            }
        }
        return result;
    }
}
