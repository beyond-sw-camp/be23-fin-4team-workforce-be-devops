package com._team._team.attendance.service;

import com._team._team.attendance.domain.DailyAttendance;
import com._team._team.attendance.domain.OvertimePolicy;
import com._team._team.attendance.domain.enums.AttendanceStatus;
import com._team._team.attendance.domain.enums.ViolationType;
import com._team._team.attendance.dto.resDto.WorkTimeSummaryResDto;
import com._team._team.attendance.dto.vo.LaborLawViolation;
import com._team._team.attendance.repository.DailyAttendanceRepository;
import com._team._team.attendance.repository.OvertimePolicyRepository;
import com._team._team.attendance.repository.OvertimeRequestRepository;
import com._team._team.dto.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 법정 근로시간 한도 검증
 */
@Service
@Transactional(readOnly = true)
public class LaborLawValidator {

    // 근로기준법 54조 법정 휴게시간 기준
    private static final int FOUR_HOURS = 240;
    private static final int EIGHT_HOURS = 480;
    private static final int MIN_BREAK_OVER_FOUR = 30;
    private static final int MIN_BREAK_OVER_EIGHT = 60;

    // 근로기준법 55조 주휴수당 최소 소정근로, 주 15시간 = 900분
    private static final int WEEKLY_HOLIDAY_MIN_MINUTES = 900;

    private final OvertimePolicyRepository overtimePolicyRepository;
    private final OvertimeRequestRepository overtimeRequestRepository;
    private final DailyAttendanceRepository dailyAttendanceRepository;

    @Autowired
    public LaborLawValidator(OvertimePolicyRepository overtimePolicyRepository,
                             OvertimeRequestRepository overtimeRequestRepository,
                             DailyAttendanceRepository dailyAttendanceRepository) {
        this.overtimePolicyRepository = overtimePolicyRepository;
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.dailyAttendanceRepository = dailyAttendanceRepository;
    }

    /**
     * 초과근무 신청 시간을 기존 승인분에 더했을 때 한도 초과가 예상되는지 체크
     */
    public List<LaborLawViolation> validateBeforeOvertimeSubmit(
            UUID memberId,
            UUID companyId,
            LocalDate targetDate,
            int requestedMinutes) {

        List<LaborLawViolation> violations = new ArrayList<>();

        OvertimePolicy policy = overtimePolicyRepository
                .findEffective(companyId, targetDate)
                .orElse(null);
        if (policy == null) {
            return violations;
        }

        // 일 연장근로 한도 체크 (회사 정책)
        checkDailyOvertimeLimit(memberId, targetDate, requestedMinutes, policy, violations);

        // 주 연장근로 한도 체크 (법정 12시간)
        checkWeeklyOvertimeLimit(memberId, targetDate, requestedMinutes, policy, violations);

        // 월 연장근로 한도 체크 (회사 정책)
        checkMonthlyOvertimeLimit(memberId, targetDate, requestedMinutes, policy, violations);

        // 주 총 근로시간 한도 체크 (정규 + 연장 합계, 법정 52시간)
        checkWeeklyTotalLimit(memberId, targetDate, requestedMinutes, policy, violations);

        return violations;
    }

    /**
     * 특정 주의 누적 근로시간이 법정 한도를 넘었는지 사후 검증
     */
    public List<LaborLawViolation> validateWeeklyLimit(UUID memberId,
                                                       UUID companyId,
                                                       LocalDate date) {

        List<LaborLawViolation> violations = new ArrayList<>();

        OvertimePolicy policy = overtimePolicyRepository
                .findEffective(companyId, date)
                .orElse(null);
        if (policy == null) {
            return violations;
        }

        // 주 기준 일요일 시작 ~ 토요일 끝 (한국 달력 일반 표기)
        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        // 주 총 근로시간 체크 (52시간)
        int totalWorked = sumWorkedMinutesInRange(memberId, weekStart, weekEnd);
        if (totalWorked > policy.getWeeklyTotalLimitMinutes()) {
            violations.add(new LaborLawViolation(
                    ViolationType.WEEKLY_TOTAL_EXCEEDED,
                    String.format("주 총 근로시간 초과 (%d분 / 한도 %d분)",
                            totalWorked, policy.getWeeklyTotalLimitMinutes()),
                    totalWorked,
                    policy.getWeeklyTotalLimitMinutes()
            ));
        }

        // 주 연장근로 한도 체크
        // 법정 최대 12시간(근기법 53조), 회사가 더 엄격하게 설정 가능
        int weeklyOt = overtimeRequestRepository
                .sumApprovedMinutesInRange(memberId, weekStart, weekEnd);
        if (weeklyOt > policy.getWeeklyOvertimeLimitMinutes()) {
            violations.add(new LaborLawViolation(
                    ViolationType.WEEKLY_OVERTIME_EXCEEDED,
                    String.format("주 연장근로 한도 초과 (%d분 / 한도 %d분)",
                            weeklyOt, policy.getWeeklyOvertimeLimitMinutes()),
                    weeklyOt,
                    policy.getWeeklyOvertimeLimitMinutes()
            ));
        }

        return violations;
    }

    /**
     * 월 연장근로 한도 검증
     * 관리자 대시보드에서 "이번 달 한도 초과자" 리스트
     */
    public Optional<LaborLawViolation> validateMonthlyOvertimeLimit(UUID memberId,
                                                                    UUID companyId,
                                                                    LocalDate yearMonth) {
        OvertimePolicy policy = overtimePolicyRepository
                .findEffective(companyId, yearMonth)
                .orElse(null);
        if (policy == null || policy.getMonthlyOvertimeLimitMinutes() == null) {
            return Optional.empty();
        }

        LocalDate monthStart = yearMonth.withDayOfMonth(1);
        LocalDate monthEnd = yearMonth.withDayOfMonth(yearMonth.lengthOfMonth());

        int monthlyOt = overtimeRequestRepository
                .sumApprovedMinutesInRange(memberId, monthStart, monthEnd);

        if (monthlyOt > policy.getMonthlyOvertimeLimitMinutes()) {
            return Optional.of(new LaborLawViolation(
                    ViolationType.MONTHLY_OVERTIME_EXCEEDED,
                    String.format("월 연장근로 한도 초과 (%d분 / 한도 %d분)",
                            monthlyOt, policy.getMonthlyOvertimeLimitMinutes()),
                    monthlyOt,
                    policy.getMonthlyOvertimeLimitMinutes()
            ));
        }
        return Optional.empty();
    }

    // 일 연장근로 한도 체크
    private void checkDailyOvertimeLimit(UUID memberId,
                                         LocalDate date,
                                         int requestedMinutes,
                                         OvertimePolicy policy,
                                         List<LaborLawViolation> violations) {
        if (policy.getDailyOvertimeLimitMinutes() == null) {
            return;
        }
        int existing = overtimeRequestRepository.sumApprovedMinutes(memberId, date);
        int projected = existing + requestedMinutes;

        if (projected > policy.getDailyOvertimeLimitMinutes()) {
            violations.add(new LaborLawViolation(
                    ViolationType.DAILY_OVERTIME_EXCEEDED,
                    String.format("일 연장근로 한도 초과 예상 (%d분 / 한도 %d분)",
                            projected, policy.getDailyOvertimeLimitMinutes()),
                    projected,
                    policy.getDailyOvertimeLimitMinutes()
            ));
        }
    }

    // 주 연장근로 한도 체크
    private void checkWeeklyOvertimeLimit(UUID memberId,
                                          LocalDate date,
                                          int requestedMinutes,
                                          OvertimePolicy policy,
                                          List<LaborLawViolation> violations) {
        // 주 기준 일요일 시작 ~ 토요일 끝 (한국 달력 일반 표기)
        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        int existing = overtimeRequestRepository
                .sumApprovedMinutesInRange(memberId, weekStart, weekEnd);
        int projected = existing + requestedMinutes;

        if (projected > policy.getWeeklyOvertimeLimitMinutes()) {
            violations.add(new LaborLawViolation(
                    ViolationType.WEEKLY_OVERTIME_EXCEEDED,
                    String.format("주 연장근로 한도 초과 예상 (%d분 / 한도 %d분)",
                            projected, policy.getWeeklyOvertimeLimitMinutes()),
                    projected,
                    policy.getWeeklyOvertimeLimitMinutes()
            ));
        }
    }

    // 월 연장근로 한도 체크
    private void checkMonthlyOvertimeLimit(UUID memberId,
                                           LocalDate date,
                                           int requestedMinutes,
                                           OvertimePolicy policy,
                                           List<LaborLawViolation> violations) {
        if (policy.getMonthlyOvertimeLimitMinutes() == null) {
            return;
        }
        LocalDate monthStart = date.withDayOfMonth(1);
        LocalDate monthEnd = date.withDayOfMonth(date.lengthOfMonth());

        int existing = overtimeRequestRepository
                .sumApprovedMinutesInRange(memberId, monthStart, monthEnd);
        int projected = existing + requestedMinutes;

        if (projected > policy.getMonthlyOvertimeLimitMinutes()) {
            violations.add(new LaborLawViolation(
                    ViolationType.MONTHLY_OVERTIME_EXCEEDED,
                    String.format("월 연장근로 한도 초과 예상 (%d분 / 한도 %d분)",
                            projected, policy.getMonthlyOvertimeLimitMinutes()),
                    projected,
                    policy.getMonthlyOvertimeLimitMinutes()
            ));
        }
    }

    // 주 총 근로시간 한도 체크 (정규 근무 + 연장 근무 합계)
    private void checkWeeklyTotalLimit(UUID memberId,
                                       LocalDate date,
                                       int requestedMinutes,
                                       OvertimePolicy policy,
                                       List<LaborLawViolation> violations) {
        if (policy.getWeeklyTotalLimitMinutes() == null) {
            return;
        }
        // 주 기준 일요일 시작 ~ 토요일 끝 (한국 달력 일반 표기)
        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        int existingWorked = sumWorkedMinutesInRange(memberId, weekStart, weekEnd);
        int projected = existingWorked + requestedMinutes;

        if (projected > policy.getWeeklyTotalLimitMinutes()) {
            violations.add(new LaborLawViolation(
                    ViolationType.WEEKLY_TOTAL_EXCEEDED,
                    String.format("주 총 근로시간 한도 초과 예상 (%d분 / 한도 %d분, 주 52시간)",
                            projected, policy.getWeeklyTotalLimitMinutes()),
                    projected,
                    policy.getWeeklyTotalLimitMinutes()
            ));
        }
    }

    // 주, 월 범위의 workedMinutes 합계
    private int sumWorkedMinutesInRange(UUID memberId, LocalDate from, LocalDate to) {
        return dailyAttendanceRepository
                .findAllByMemberInRange(memberId, from, to)
                .stream()
                .mapToInt(d -> d.getWorkedMinutes() == null ? 0 : d.getWorkedMinutes())
                .sum();
    }

    /**
     * 주간 근무시간 요약 조회, 직원 본인 화면용
     */
    public WorkTimeSummaryResDto getWeeklySummary(UUID memberId, UUID companyId,
                                                  LocalDate date) {
        OvertimePolicy policy = overtimePolicyRepository
                .findEffective(companyId, date)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "연장근로 정책이 설정되지 않았습니다."));

        // 주 기준 일요일 시작 ~ 토요일 끝 (한국 달력 일반 표기)
        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        List<DailyAttendance> weekly = dailyAttendanceRepository
                .findAllByMemberInRange(memberId, weekStart, weekEnd);

        int totalWorked = weekly.stream()
                .mapToInt(d -> d.getWorkedMinutes() == null ? 0 : d.getWorkedMinutes())
                .sum();
        // 주 연장근무 - (실제 발생한 연장근무, 월 누적과 동일 기준)
        int overtimeApproved = weekly.stream()
                .mapToInt(d -> d.getOvertimeMinutes() == null ? 0 : d.getOvertimeMinutes())
                .sum();

        int totalLimit = policy.getWeeklyTotalLimitMinutes();
        int overtimeLimit = policy.getWeeklyOvertimeLimitMinutes();

        // 월 누적 초과근무
        LocalDate monthStart = date.withDayOfMonth(1);
        List<DailyAttendance> monthly = dailyAttendanceRepository
                .findAllByMemberInRange(memberId, monthStart, date);
        int monthlyOvertime = monthly.stream()
                .mapToInt(d -> d.getOvertimeMinutes() == null ? 0 : d.getOvertimeMinutes())
                .sum();
        Integer monthlyLimit = policy.getMonthlyOvertimeLimitMinutes();
        Integer monthlyUsagePercent = monthlyLimit != null && monthlyLimit > 0
                ? calcPercent(monthlyOvertime, monthlyLimit) : null;

        // 주휴수당 자격, 주 소정근로 15시간 이상 + 개근 (ABSENT 0일)
        int absentDays = (int) weekly.stream()
                .filter(d -> d.getStatus() == AttendanceStatus.ABSENT)
                .count();
        boolean meetsMinutes = totalWorked >= WEEKLY_HOLIDAY_MIN_MINUTES;
        boolean noAbsent = absentDays == 0;
        boolean eligible = meetsMinutes && noAbsent;
        String reason = buildWeeklyHolidayReason(meetsMinutes, noAbsent, absentDays, totalWorked);

        return WorkTimeSummaryResDto.builder()
                .weekStart(weekStart)
                .weekEnd(weekEnd)
                .totalWorkedMinutes(totalWorked)
                .totalLimitMinutes(totalLimit)
                .totalUsagePercent(calcPercent(totalWorked, totalLimit))
                .overtimeApprovedMinutes(overtimeApproved)
                .overtimeLimitMinutes(overtimeLimit)
                .overtimeUsagePercent(calcPercent(overtimeApproved, overtimeLimit))
                .monthlyOvertimeMinutes(monthlyOvertime)
                .monthlyOvertimeLimitMinutes(monthlyLimit)
                .monthlyOvertimeUsagePercent(monthlyUsagePercent)
                .weeklyHolidayEligible(eligible)
                .weeklyHolidayMinRequiredMinutes(WEEKLY_HOLIDAY_MIN_MINUTES)
                .weeklyAbsentDays(absentDays)
                .weeklyHolidayReason(reason)
                .build();
    }

    // 자격 여부에 따른 사유 문구 생성
    private String buildWeeklyHolidayReason(boolean meetsMinutes, boolean noAbsent,
                                            int absentDays, int totalWorked) {
        if (meetsMinutes && noAbsent) {
            return "주 15시간 이상 + 개근으로 주휴수당 자격 충족";
        }
        if (!meetsMinutes && !noAbsent) {
            return "주 15시간 미달(" + totalWorked + "분) + 결근 " + absentDays + "일";
        }
        if (!meetsMinutes) {
            return "주 소정근로 15시간 미달 (현재 " + totalWorked + "분)";
        }
        return "결근 " + absentDays + "일로 해당 주 자격 상실";
    }

    /**
     * 사용률 계산
     */
    private int calcPercent(int part, int whole) {
        return whole == 0 ? 0 : (int) Math.round((double) part / whole * 100);
    }
}