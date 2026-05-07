package com._team._team.salary.service;

import com._team._team.attendance.domain.DailyAttendance;
import com._team._team.attendance.domain.LeaveRequest;
import com._team._team.attendance.domain.enums.AttendanceStatus;
import com._team._team.attendance.repository.DailyAttendanceRepository;
import com._team._team.attendance.repository.LeaveRequestRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 승인된 휴가신청을 일일근태에 반영
 * 근태 마감 배치에서 호출, 휴가일 근태 상태를 LEAVE/HALF 로 확정
 */
@Slf4j
@Component
public class LeaveAttendanceReflector {

    private final LeaveRequestRepository leaveRequestRepository;
    private final DailyAttendanceRepository dailyAttendanceRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public LeaveAttendanceReflector(LeaveRequestRepository leaveRequestRepository,
                                    DailyAttendanceRepository dailyAttendanceRepository,
                                    ObjectMapper objectMapper) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.dailyAttendanceRepository = dailyAttendanceRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 비연속 사용 날짜 JSON 파싱, 파싱 실패 또는 비어있으면 빈 Set
     */
    private Set<LocalDate> parsePlannedDates(LeaveRequest leave) {
        String json = leave.getPlannedDatesJson();
        if (json == null || json.isBlank()) return Collections.emptySet();
        try {
            List<String> raw = objectMapper.readValue(json, new TypeReference<>() {});
            return raw.stream()
                    .map(LocalDate::parse)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("[LeaveReflect] plannedDatesJson 파싱 실패 leaveRequestId={}",
                    leave.getLeaveRequestId(), e);
            return Collections.emptySet();
        }
    }
    /**
     * 특정 날짜의 승인 휴가를 모든 직원에 대해 일괄 반영
     * 비연속 휴가계획이 있는 휴가요청은 그 날짜에 포함된 경우에만 반영
     */
    @Transactional
    public int reflectForDate(LocalDate targetDate) {

        // 해당 날짜 범위에 속하는 모든 승인 휴가 조회 (전 직원)
        List<LeaveRequest> activeLeaves = leaveRequestRepository
                .findAllActiveOnDate(targetDate);

        int count = 0;
        for (LeaveRequest leave : activeLeaves) {
            // 비연속 plannedDates 있으면 그 날짜만 적용 대상, targetDate 가 없으면 skip
            Set<LocalDate> planned = parsePlannedDates(leave);
            if (!planned.isEmpty() && !planned.contains(targetDate)) {
                continue;
            }
            try {
                applyLeaveToAttendance(leave, targetDate);
                count++;
            } catch (Exception e) {
                log.error("[LeaveReflect] 반영 실패 leaveRequestId={}, date={}",
                        leave.getLeaveRequestId(), targetDate, e);
            }
        }
        log.info("[LeaveReflect] 완료 date={}, count={}", targetDate, count);
        return count;
    }

    /**
     * 사후 휴가 승인 시 호출, 휴가 기간 내 날짜의 일일근태에 반영
     * 마감 후 케이스 재계산용
     */
    @Transactional
    public List<UUID> reflectForLeaveRequest(LeaveRequest leave) {
        List<UUID> affected = new ArrayList<>();
        Set<LocalDate> planned = parsePlannedDates(leave);

        List<LocalDate> targets = new ArrayList<>();
        if (!planned.isEmpty()) {
            targets.addAll(planned);
        } else {
            LocalDate cur = leave.getStartDate();
            LocalDate end = leave.getEndDate();
            while (!cur.isAfter(end)) {
                targets.add(cur);
                cur = cur.plusDays(1);
            }
        }

        for (LocalDate target : targets) {
            try {
                UUID daId = applyLeaveToAttendance(leave, target);
                if (daId != null) affected.add(daId);
            } catch (Exception e) {
                log.error("[LeaveReflect] 사후반영 실패 leaveRequestId={}, date={}",
                        leave.getLeaveRequestId(), target, e);
            }
        }
        log.info("[LeaveReflect] 사후반영 완료 leaveRequestId={}, affected={}",
                leave.getLeaveRequestId(), affected.size());
        return affected;
    }

    /**
     * 한 건의 휴가승인요청 일일근태에 반영
     * 1. usageDays 로 status 결정 (1.0 미만이면 HALF, 이상이면 LEAVE)
     * 2. 일일 근태 조회
     * 있으면: 기존 출근 기록 보존, status 만 변경
     *  예, 오전 반차 후 14시 출근 -> status=HALF, workedMinutes=240 유지
     * 없으면: LEAVE 상태로 새로 생성 (workedMinutes=0)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private UUID applyLeaveToAttendance(LeaveRequest leave, LocalDate targetDate) {

        // 1. usageDays 기준 status 결정
        // 반차-> 1.0 미만 = HALF, 전일 휴가 = LEAVE
        AttendanceStatus newStatus = leave.getUsageDays() < 1.0
                ? AttendanceStatus.HALF
                : AttendanceStatus.LEAVE;

        // 2. 해당 날짜 기존 DailyAttendance 존재 여부 확인
        Optional<DailyAttendance> existing = dailyAttendanceRepository
                .findByMemberIdAndAttendanceDate(leave.getMemberId(), targetDate);


        if (existing.isPresent()) {
            // 이미 출근 기록 있음, status 만 업데이트
            // 반차 + 오후 출근 케이스, 무단 출근 후 사후 휴가 처리 케이스 등 대응
            DailyAttendance da = existing.get();
            da.updateStatus(newStatus);
            return da.getDailyAttendanceId();
        }
        // 출근 기록 없음, LEAVE/HALF 로 신규 생성
        DailyAttendance newAttendance = DailyAttendance.builder()
                .memberId(leave.getMemberId())
                .companyId(leave.getCompanyId())
                .attendanceDate(targetDate)
                .status(newStatus)
                .workedMinutes(0)
                .overtimeMinutes(0)
                .build();
        DailyAttendance saved = dailyAttendanceRepository.save(newAttendance);

        log.debug("[LeaveReflect] 신규 휴가 근태 생성, " +
                        "memberId={}, date={}, status={}",
                leave.getMemberId(), targetDate, newStatus);
        return saved.getDailyAttendanceId();
    }
}
