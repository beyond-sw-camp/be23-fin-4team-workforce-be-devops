package com._team._team.attendance.service;

import com._team._team.attendance.domain.OvertimePolicy;
import com._team._team.attendance.dto.resDto.OvertimeUsageResDto;
import com._team._team.attendance.repository.CompanyHolidayRepository;
import com._team._team.attendance.repository.DailyAttendanceRepository;
import com._team._team.attendance.repository.DailyAttendanceRepository.MemberOvertimeMinutesRow;
import com._team._team.attendance.repository.OvertimePolicyRepository;
import com._team._team.attendance.repository.OvertimeRequestRepositoryQuerydsl.MemberApprovedMinutesRow;
import com._team._team.attendance.repository.OvertimeRequestRepository;
import com._team._team.salary.feignClients.dto.MemberResDto;
import com._team._team.salary.service.CachedMemberLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 직원 월별 초과근무시간 누적 + 회사 월 한도 (주52시간/월한도) 모니터링
 * 관리자 [초과 근무 현황] 화면용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OvertimeUsageService {

    private final OvertimePolicyRepository overtimePolicyRepository;
    private final OvertimeRequestRepository overtimeRequestRepository;
    private final DailyAttendanceRepository dailyAttendanceRepository;
    private final CachedMemberLookupService cachedMemberLookup;
    private final CompanyHolidayRepository companyHolidayRepository;

    public enum PeriodMode { WEEK, MONTH }

    @Transactional(readOnly = true)
    public List<OvertimeUsageResDto> getStatus(UUID companyId, LocalDate baseDate) {
        return getStatus(companyId, baseDate, PeriodMode.MONTH);
    }

    /**
     * 회사 전체 직원 -> 기준일이 속한 주(월~일) 또는 월(1일~말일) 범위 누적
     */
    @Transactional(readOnly = true)
    public List<OvertimeUsageResDto> getStatus(UUID companyId, LocalDate baseDate, PeriodMode mode) {
        LocalDate from;
        LocalDate to;
        if (mode == PeriodMode.WEEK) {
            // 일요일 ~ 토요일 (FE picker dayjs default 와 일치)
            int dow = baseDate.getDayOfWeek().getValue() % 7; // 일=0, 월=1, ..., 토=6
            from = baseDate.minusDays(dow);
            to = from.plusDays(6);
        } else {
            YearMonth ym = YearMonth.from(baseDate);
            from = ym.atDay(1);
            to = ym.atEndOfMonth();
        }

        // 회사 활성 OvertimePolicy - periodMode 에 따라 주 한도 / 월 한도 추출 (없으면 null)
        OvertimePolicy policy = overtimePolicyRepository
                .findByCompanyIdAndEffectiveToIsNull(companyId)
                .orElse(null);
        Integer monthlyLimit = policy == null ? null : (
                mode == PeriodMode.WEEK
                        ? policy.getWeeklyOvertimeLimitMinutes()
                        : policy.getMonthlyOvertimeLimitMinutes()
        );

        // 멤버 목록
        List<MemberResDto> members = cachedMemberLookup.getMembersByCompany(companyId);
        if (members.isEmpty()) return Collections.emptyList();
        Map<UUID, MemberResDto> memberMap = members.stream()
                .collect(Collectors.toMap(MemberResDto::getMemberId, m -> m, (a, b) -> a));

        // 실측 초과근무시간 합
        Map<UUID, Long> actualByMember = dailyAttendanceRepository
                .sumActualOvertimeByCompanyAndRange(companyId, from, to)
                .stream()
                .collect(Collectors.toMap(MemberOvertimeMinutesRow::getMemberId,
                        MemberOvertimeMinutesRow::getSumMinutes,
                        (a, b) -> a));

        // 승인된 초과근무시간 합
        Map<UUID, Long> approvedByMember = overtimeRequestRepository
                .sumApprovedMinutesByCompanyAndRange(companyId, from, to)
                .stream()
                .collect(Collectors.toMap(MemberApprovedMinutesRow::memberId,
                        MemberApprovedMinutesRow::sumMinutes,
                        (a, b) -> a));

        // 총 근무시간 (정규+초과) 합
        Map<UUID, Long> workedByMember = dailyAttendanceRepository
                .sumWorkedByCompanyAndRange(companyId, from, to)
                .stream()
                .collect(Collectors.toMap(MemberOvertimeMinutesRow::getMemberId,
                        MemberOvertimeMinutesRow::getSumMinutes,
                        (a, b) -> a));

        // 일자별 worked/overtime - 화면 셀 색칠용
        Map<UUID, Map<LocalDate, int[]>> dailyByMember = new HashMap<>();
        for (var row : dailyAttendanceRepository.findDailyMinutesByCompanyAndRange(companyId, from, to)) {
            dailyByMember
                    .computeIfAbsent(row.getMemberId(), k -> new HashMap<>())
                    .merge(row.getAttendanceDate(),
                            new int[] { row.getWorkedMinutes() == null ? 0 : row.getWorkedMinutes(),
                                        row.getOvertimeMinutes() == null ? 0 : row.getOvertimeMinutes() },
                            (a, b) -> new int[] { a[0] + b[0], a[1] + b[1] });
        }

        // 세 집계 중 한쪽이라도 값이 있는 멤버 union
        Set<UUID> memberIds = new HashSet<>();
        memberIds.addAll(actualByMember.keySet());
        memberIds.addAll(approvedByMember.keySet());
        memberIds.addAll(workedByMember.keySet());
        memberIds.addAll(dailyByMember.keySet());

        // 기간 단위별 bucket key 미리 계산
        WeekFields wf = WeekFields.of(Locale.KOREA);
        List<LocalDate> allDates = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) allDates.add(d);

        // 회사 공휴일 (해당 기간) - 주말 외 추가 빨간 날
        Set<LocalDate> holidays = companyHolidayRepository
                .findByCompanyIdAndHolidayDateBetweenAndDelYn(companyId, from, to, "N")
                .stream()
                .map(h -> h.getHolidayDate())
                .collect(Collectors.toSet());

        return memberIds.stream()
                .map(mId -> {
                    int actual = actualByMember.getOrDefault(mId, 0L).intValue();
                    int approved = approvedByMember.getOrDefault(mId, 0L).intValue();
                    int worked = workedByMember.getOrDefault(mId, 0L).intValue();
                    int limit = monthlyLimit != null ? monthlyLimit : 0;
                    double pct = limit > 0 ? (actual * 100.0 / limit) : 0.0;
                    int exceed = limit > 0 ? Math.max(0, actual - limit) : 0;
                    MemberResDto m = memberMap.get(mId);
                    Map<LocalDate, int[]> daily = dailyByMember.getOrDefault(mId, Collections.emptyMap());
                    List<OvertimeUsageResDto.Bucket> buckets = buildBuckets(allDates, daily, mode, wf, holidays);
                    return OvertimeUsageResDto.builder()
                            .memberId(mId)
                            .name(m != null ? m.getName() : null)
                            .organizationName(m != null ? m.getOrganizationName() : null)
                            .jobGradeName(m != null ? m.getJobGradeName() : null)
                            .jobTitleName(m != null ? m.getJobTitleName() : null)
                            .actualOvertimeMinutes(actual)
                            .approvedMinutes(approved)
                            .fixedLimit(monthlyLimit)
                            .usagePercent(Math.round(pct * 10.0) / 10.0)
                            .exceedMinutes(exceed)
                            .totalWorkMinutes(worked)
                            .buckets(buckets)
                            .build();
                })
                .sorted((a, b) -> Double.compare(
                        b.getUsagePercent() != null ? b.getUsagePercent() : 0.0,
                        a.getUsagePercent() != null ? a.getUsagePercent() : 0.0))
                .toList();
    }

    /**
     * 본인 OT 현황 (단건)
     */
    @Transactional(readOnly = true)
    public OvertimeUsageResDto getMyStatus(UUID companyId, UUID memberId, LocalDate baseDate) {
        return getStatus(companyId, baseDate).stream()
                .filter(d -> memberId.equals(d.getMemberId()))
                .findFirst()
                .orElse(null);
    }

    // 기간 분할별 bucket 생성 (WEEK -> 7일, MONTH -> ISO 주차별)
    private List<OvertimeUsageResDto.Bucket> buildBuckets(List<LocalDate> allDates,
                                                          Map<LocalDate, int[]> daily,
                                                          PeriodMode mode,
                                                          WeekFields wf,
                                                          Set<LocalDate> companyHolidays) {
        if (mode == PeriodMode.WEEK) {
            // 일자별 7개
            List<OvertimeUsageResDto.Bucket> list = new ArrayList<>();
            String[] dowKo = { "월", "화", "수", "목", "금", "토", "일" };
            for (LocalDate d : allDates) {
                int[] mins = daily.getOrDefault(d, new int[] { 0, 0 });
                int idx = d.getDayOfWeek().getValue() - 1; // 1=월
                int dowVal = d.getDayOfWeek().getValue(); // 6=토, 7=일
                boolean isHoliday = dowVal == 6 || dowVal == 7 || companyHolidays.contains(d);
                list.add(OvertimeUsageResDto.Bucket.builder()
                        .key(d.toString())
                        .label(d.getMonthValue() + "/" + d.getDayOfMonth() + "(" + dowKo[idx] + ")")
                        .workedMinutes(mins[0])
                        .overtimeMinutes(mins[1])
                        .holiday(isHoliday)
                        .build());
            }
            return list;
        }
        // MONTH - 주차별 합산
        Map<String, int[]> byWeek = new TreeMap<>();
        Map<String, String> labelByWeek = new HashMap<>();
        for (LocalDate d : allDates) {
            int week = d.get(wf.weekOfWeekBasedYear());
            int weekYear = d.get(wf.weekBasedYear());
            String key = weekYear + "-W" + (week < 10 ? "0" + week : week);
            int[] mins = daily.getOrDefault(d, new int[] { 0, 0 });
            byWeek.merge(key, mins, (a, b) -> new int[] { a[0] + b[0], a[1] + b[1] });
            labelByWeek.putIfAbsent(key, week + "주차");
        }
        List<OvertimeUsageResDto.Bucket> list = new ArrayList<>();
        for (var e : byWeek.entrySet()) {
            list.add(OvertimeUsageResDto.Bucket.builder()
                    .key(e.getKey())
                    .label(labelByWeek.get(e.getKey()))
                    .workedMinutes(e.getValue()[0])
                    .overtimeMinutes(e.getValue()[1])
                    .build());
        }
        return list;
    }
}
