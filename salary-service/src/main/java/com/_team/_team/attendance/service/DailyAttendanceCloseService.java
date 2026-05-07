package com._team._team.attendance.service;

import com._team._team.attendance.domain.OvertimePolicy;
import com._team._team.attendance.domain.enums.ClosureStatus;
import com._team._team.attendance.dto.vo.ResolvedSchedule;
import com._team._team.attendance.dto.vo.WorkTimeBreakdown;
import com._team._team.attendance.feignClients.ApprovalServiceClient;
import com._team._team.attendance.repository.*;
import com._team._team.attendance.domain.DailyAttendance;
import com._team._team.attendance.domain.WorkSchedule;
import com._team._team.attendance.domain.enums.AttendanceStatus;
import com._team._team.salary.service.LeaveAttendanceReflector;
import com._team._team.salary.service.LeaveOfAbsenceAttendanceReflector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 일일 근태 2단계 마감 서비스
 * DRAFT(06:00)는 데이터 안 건드리고 알림만, FINAL(14:00)은 그때도 안 고쳐졌으면 자동 마감
 */
@Slf4j
@Service
public class DailyAttendanceCloseService {

    private final DailyAttendanceRepository dailyAttendanceRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final ApprovalServiceClient approvalServiceClient;
    private final AttendanceAnomalyDetector anomalyDetector;
    private final ScheduleResolver scheduleResolver;
    private final WorkTimeClassifier workTimeClassifier;
    private final CompanyHolidayRepository companyHolidayRepository;
    private final OvertimeRequestRepository overtimeRequestRepository;
    private final OvertimePolicyRepository overtimePolicyRepository;
    private final LeaveAttendanceReflector leaveAttendanceReflector;
    private final LeaveOfAbsenceAttendanceReflector leaveOfAbsenceAttendanceReflector;
    private final MemberLeaveOfAbsenceService memberLeaveOfAbsenceService;

    @Autowired
    public DailyAttendanceCloseService(
            DailyAttendanceRepository dailyAttendanceRepository,
            WorkScheduleRepository workScheduleRepository,
            ApprovalServiceClient approvalServiceClient,
            AttendanceAnomalyDetector anomalyDetector,
            ScheduleResolver scheduleResolver,
            WorkTimeClassifier workTimeClassifier,
            CompanyHolidayRepository companyHolidayRepository,
            OvertimeRequestRepository overtimeRequestRepository,
            OvertimePolicyRepository overtimePolicyRepository,
            LeaveAttendanceReflector leaveAttendanceReflector,
            LeaveOfAbsenceAttendanceReflector leaveOfAbsenceAttendanceReflector,
            MemberLeaveOfAbsenceService memberLeaveOfAbsenceService) {
        this.dailyAttendanceRepository = dailyAttendanceRepository;
        this.workScheduleRepository = workScheduleRepository;
        this.approvalServiceClient = approvalServiceClient;
        this.anomalyDetector = anomalyDetector;
        this.scheduleResolver = scheduleResolver;
        this.workTimeClassifier = workTimeClassifier;
        this.companyHolidayRepository = companyHolidayRepository;
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.overtimePolicyRepository = overtimePolicyRepository;
        this.leaveAttendanceReflector = leaveAttendanceReflector;
        this.leaveOfAbsenceAttendanceReflector = leaveOfAbsenceAttendanceReflector;
        this.memberLeaveOfAbsenceService = memberLeaveOfAbsenceService;
    }

    // OPEN 상태 근태를 DRAFT 로 일괄 전이 + 재계산 + 이상 감지
    public DraftResult processDraft(LocalDate targetDate) {

        // 0단계: 승인된 휴가 반영
        int leaveReflected = leaveAttendanceReflector.reflectForDate(targetDate);
        log.info("[DRAFT] 휴가 반영 count={}", leaveReflected);

        // 1단계: 휴직 -> 일일근태에 LEAVE
        int loaReflected = leaveOfAbsenceAttendanceReflector.reflectForDate(targetDate);
        log.info("[DRAFT] 휴직 근태 반영 count={}", loaReflected);

        // 2단계 -> endDate < 오늘 인 ACTIVE 휴직 ENDED (1~2단계 이후)
        int leaveOfAbsenceEnded = memberLeaveOfAbsenceService.endExpired(LocalDate.now());
        log.info("[DRAFT] 휴직 자연 종료 count={}", leaveOfAbsenceEnded);

        // 3단계: OPEN 상태 DRAFT 처리
        List<DailyAttendance> openList = dailyAttendanceRepository
                .findAllByAttendanceDateAndClosureStatus(targetDate, ClosureStatus.OPEN);

        int drafted = 0;
        int failed = 0;

        for (DailyAttendance da : openList) {
            try {
                draftOne(da.getDailyAttendanceId());
                drafted++;
            } catch (Exception e) {
                log.error("[DRAFT] 처리 실패 dailyId={}, msg={}",
                        da.getDailyAttendanceId(), e.getMessage(), e);
                failed++;
            }
        }

        // 휴가일 출근 건 감지
        List<DailyAttendance> leaveButClockIn =
                dailyAttendanceRepository.findLeaveDateWithClockIn(
                        targetDate,
                        List.of(AttendanceStatus.LEAVE, AttendanceStatus.HALF));

        // 기존 이상 감지 + 알림 발송 로직 유지
        anomalyDetector.detect(targetDate);

        log.info("[DRAFT] targetDate={} 전이성공={} 실패={} 휴가출근={}",
                targetDate, drafted, failed, leaveButClockIn.size());

        return new DraftResult(drafted, failed, leaveButClockIn.size());
    }

    // 단건 DRAFT 처리
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void draftOne(UUID dailyAttendanceId) {
        DailyAttendance da = dailyAttendanceRepository.findById(dailyAttendanceId)
                .orElseThrow(() -> new IllegalStateException(
                        "출근 기록이 없습니다 : " + dailyAttendanceId));

        // 이미 전이된 건은 skip
        if (da.getClosureStatus() != ClosureStatus.OPEN) {
            return;
        }

        // 스케줄 해석 + 재계산
        ResolvedSchedule schedule = scheduleResolver.resolve(
                da.getMemberId(), da.getCompanyId(), da.getAttendanceDate());

        // 공휴일 여부
        boolean isHoliday = companyHolidayRepository.existsByCompanyIdAndHolidayDate(
                da.getCompanyId(), da.getAttendanceDate());

        // 승인된 연장근무 시간 조회
        Integer approvedOt = overtimeRequestRepository.sumApprovedMinutes(
                da.getMemberId(), da.getAttendanceDate());
        int approvedMinutes = approvedOt == null ? 0 : approvedOt;

        // 연장근로 정책 조회 (라운딩 단위 사용)
        OvertimePolicy otPolicy = overtimePolicyRepository
                .findEffective(da.getCompanyId(), da.getAttendanceDate())
                .orElseThrow(() -> new IllegalStateException(
                        "OvertimePolicy not found. companyId=" + da.getCompanyId()));

        // 근무 시간 분류
        WorkTimeBreakdown breakdown = workTimeClassifier.classify(
                da, schedule, isHoliday, approvedMinutes, otPolicy.getOvertimeFloorMinutes());

        // 지급 대상 총 분, 연장 분 저장
        da.applyWorkResult(
                breakdown.totalPayableMinutes(),
                breakdown.overtimeMinutes());

        da.transitionClosure(ClosureStatus.DRAFT);
    }

    // DRAFT 전체를 FINALIZED 로 전이, 미퇴근은 자동 퇴근 세팅
    public FinalResult processFinal(LocalDate targetDate) {
        List<DailyAttendance> draftList = dailyAttendanceRepository
                .findAllByAttendanceDateAndClosureStatus(targetDate, ClosureStatus.DRAFT);

        int finalized = 0;
        int autoClockOut = 0;
        int failed = 0;

        for (DailyAttendance da : draftList) {
            try {
                boolean auto = finalizeOne(da.getDailyAttendanceId(), targetDate);
                finalized++;
                if (auto) autoClockOut++;
            } catch (Exception e) {
                log.error("[FINAL] 처리 실패 dailyId={}, msg={}",
                        da.getDailyAttendanceId(), e.getMessage(), e);
                failed++;
            }
        }

        log.info("[FINAL] targetDate={} 마감={} 자동퇴근={} 실패={}",
                targetDate, finalized, autoClockOut, failed);

        return new FinalResult(finalized, autoClockOut, failed);
    }

    // 단건 FINAL 처리, 자동 퇴근 발생 여부 반환
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean finalizeOne(UUID dailyAttendanceId, LocalDate targetDate) {
        DailyAttendance da = dailyAttendanceRepository.findById(dailyAttendanceId)
                .orElseThrow(() -> new IllegalStateException(
                        "출근 기록이 없습니다 : " + dailyAttendanceId));

        // DRAFT 아니면 skip
        if (da.getClosureStatus() != ClosureStatus.DRAFT) {
            return false;
        }

        // 퇴근 누락이면 자동 세팅
        boolean autoClockOutApplied = false;
        if (da.getLastClockOut() == null) {
            LocalDateTime clockOut = resolveClockOut(da, targetDate);
            da.updateClockOut(clockOut);
            autoClockOutApplied = true;
        }

        // 퇴근시각 변경됐을 수 있으니 재계산
        ResolvedSchedule schedule = scheduleResolver.resolve(
                da.getMemberId(), da.getCompanyId(), targetDate);

        // 공휴일 여부
        boolean isHoliday = companyHolidayRepository.existsByCompanyIdAndHolidayDate(
                da.getCompanyId(), targetDate);

        // 승인된 연장근무 시간 조회
        Integer approvedOt = overtimeRequestRepository.sumApprovedMinutes(
                da.getMemberId(), targetDate);
        int approvedMinutes = approvedOt == null ? 0 : approvedOt;

        // 연장근로 정책 조회 (라운딩 단위 사용)
        OvertimePolicy otPolicy = overtimePolicyRepository
                .findEffective(da.getCompanyId(), da.getAttendanceDate())
                .orElseThrow(() -> new IllegalStateException(
                        "OvertimePolicy not found. companyId=" + da.getCompanyId()));

        // 근무 시간 분류
        WorkTimeBreakdown breakdown = workTimeClassifier.classify(
                da, schedule, isHoliday, approvedMinutes, otPolicy.getOvertimeFloorMinutes());

        da.applyWorkResult(
                breakdown.totalPayableMinutes(),
                breakdown.overtimeMinutes());

        // 최종 확정
        da.transitionClosure(ClosureStatus.FINALIZED);

        return autoClockOutApplied;
    }

    /**
     * 사후 휴가 승인 반영 후 재계산
     * - LOCKED이면 스킵, LEAVE 면 무효 처리
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recalculateAfterLeaveApproval(UUID dailyAttendanceId) {
        DailyAttendance da = dailyAttendanceRepository.findById(dailyAttendanceId)
                .orElse(null);
        if (da == null) return;

        if (da.getClosureStatus() == ClosureStatus.LOCKED) {
            log.warn("[Leave] LOCKED 상태라 사후 휴가 재계산 스킵 dailyId={}", dailyAttendanceId);
            return;
        }
        // 스케줄 조회
        ResolvedSchedule schedule = scheduleResolver.resolve(
                da.getMemberId(), da.getCompanyId(), da.getAttendanceDate());
        // 휴일인지 확인
        boolean isHoliday = companyHolidayRepository.existsByCompanyIdAndHolidayDate(
                da.getCompanyId(), da.getAttendanceDate());
        // 초과근무 확인
        Integer approvedOt = overtimeRequestRepository.sumApprovedMinutes(
                da.getMemberId(), da.getAttendanceDate());
        int approvedMinutes = approvedOt == null ? 0 : approvedOt;
        // 초과 근무 정책 확인
        OvertimePolicy otPolicy = overtimePolicyRepository
                .findEffective(da.getCompanyId(), da.getAttendanceDate())
                .orElseThrow(() -> new IllegalStateException(
                        "OvertimePolicy not found. companyId=" + da.getCompanyId()));

        WorkTimeBreakdown breakdown = workTimeClassifier.classify(
                da, schedule, isHoliday, approvedMinutes, otPolicy.getOvertimeFloorMinutes());

        da.applyWorkResult(breakdown.totalPayableMinutes(), breakdown.overtimeMinutes());
    }

    /**
     * 결재 통합 정정 승인 시 재계산 + FINALIZED 직행
     * - LOCKED 만 스킵 (월마감 후)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recalculateAndFinalizeForCorrection(UUID dailyAttendanceId) {
        DailyAttendance da = dailyAttendanceRepository.findById(dailyAttendanceId)
                .orElse(null);
        if (da == null) return;

        if (da.getClosureStatus() == ClosureStatus.LOCKED) {
            log.warn("[AttendanceCorrection] LOCKED 상태라 정정 적용 불가 dailyId={}", dailyAttendanceId);
            return;
        }

        ResolvedSchedule schedule = scheduleResolver.resolve(
                da.getMemberId(), da.getCompanyId(), da.getAttendanceDate());

        boolean isHoliday = companyHolidayRepository.existsByCompanyIdAndHolidayDate(
                da.getCompanyId(), da.getAttendanceDate());

        Integer approvedOt = overtimeRequestRepository.sumApprovedMinutes(
                da.getMemberId(), da.getAttendanceDate());
        int approvedMinutes = approvedOt == null ? 0 : approvedOt;

        OvertimePolicy otPolicy = overtimePolicyRepository
                .findEffective(da.getCompanyId(), da.getAttendanceDate())
                .orElseThrow(() -> new IllegalStateException(
                        "OvertimePolicy not found. companyId=" + da.getCompanyId()));

        WorkTimeBreakdown breakdown = workTimeClassifier.classify(
                da, schedule, isHoliday, approvedMinutes, otPolicy.getOvertimeFloorMinutes());

        da.applyWorkResult(breakdown.totalPayableMinutes(), breakdown.overtimeMinutes());
        da.transitionClosure(ClosureStatus.FINALIZED);
    }

    /**
     * 정정 신청 승인 시 재계산 + FINALIZED 직행
     * - UNDER_REVIEW 상태 DA 만 처리
     * - draftOne / finalizeOne 과 동일한 분류 로직을 거쳐 workedMinutes / overtimeMinutes 갱신
     * - 자동 퇴근 채움은 안 함 (정정 시 직원이 시각을 명시했음)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recalculateAndFinalize(UUID dailyAttendanceId) {
        DailyAttendance da = dailyAttendanceRepository.findById(dailyAttendanceId)
                .orElseThrow(() -> new IllegalStateException(
                        "출근 기록이 없습니다 : " + dailyAttendanceId));

        if (da.getClosureStatus() != ClosureStatus.UNDER_REVIEW) {
            throw new IllegalStateException(
                    "검토 대기(UNDER_REVIEW) 상태가 아닙니다. status=" + da.getClosureStatus());
        }
        // 스케줄 확인
        ResolvedSchedule schedule = scheduleResolver.resolve(
                da.getMemberId(), da.getCompanyId(), da.getAttendanceDate());
        // 휴일 확인
        boolean isHoliday = companyHolidayRepository.existsByCompanyIdAndHolidayDate(
                da.getCompanyId(), da.getAttendanceDate());
        // 초과 근무 확인
        Integer approvedOt = overtimeRequestRepository.sumApprovedMinutes(
                da.getMemberId(), da.getAttendanceDate());
        int approvedMinutes = approvedOt == null ? 0 : approvedOt;
        // 초과근무 정책 확인
        OvertimePolicy otPolicy = overtimePolicyRepository
                .findEffective(da.getCompanyId(), da.getAttendanceDate())
                .orElseThrow(() -> new IllegalStateException(
                        "OvertimePolicy not found. companyId=" + da.getCompanyId()));
        // 일 근태 종합 정리
        WorkTimeBreakdown breakdown = workTimeClassifier.classify(
                da, schedule, isHoliday, approvedMinutes, otPolicy.getOvertimeFloorMinutes());
        // 근태 반영
        da.applyWorkResult(breakdown.totalPayableMinutes(), breakdown.overtimeMinutes());
        da.transitionClosure(ClosureStatus.FINALIZED);
    }

    // 자동 퇴근시각 결정 (승인된 OT > 스케줄 end > 출근+8h)
    private LocalDateTime resolveClockOut(DailyAttendance da, LocalDate targetDate) {
        try {
            LocalDateTime approvedEnd = approvalServiceClient
                    .findLatestApprovedEndAt(da.getMemberId(), targetDate);
            if (approvedEnd != null) return approvedEnd;
        } catch (Exception e) {
            log.warn("[FINAL] approval-service 조회 실패 memberId={} → schedule fallback",
                    da.getMemberId());
        }

        WorkSchedule schedule = findSchedule(da.getCompanyId(), da.getMemberId(), targetDate);
        if (schedule != null && schedule.getEndTime() != null) {
            return targetDate.atTime(schedule.getEndTime());
        }

        return da.getFirstClockIn().plusHours(8);
    }

    // 해당 일자 유효 스케줄 1건, 없으면 null
    private WorkSchedule findSchedule(UUID companyId, UUID memberId, LocalDate date) {
        List<WorkSchedule> schedules = workScheduleRepository
                .findActiveSchedules(companyId, memberId, date);
        return schedules.isEmpty() ? null : schedules.get(0);
    }

    // 결과 요약 record
    public record DraftResult(int drafted, int failed, int leaveButClockIn) {}
    public record FinalResult(int finalized, int autoClockOut, int failed) {}
}