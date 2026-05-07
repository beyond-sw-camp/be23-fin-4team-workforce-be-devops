package com._team._team.salary.service;

import com._team._team.attendance.domain.DailyAttendance;
import com._team._team.attendance.domain.MemberLeaveOfAbsence;
import com._team._team.attendance.domain.enums.AttendanceStatus;
import com._team._team.attendance.repository.DailyAttendanceRepository;
import com._team._team.attendance.repository.MemberLeaveOfAbsenceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 승인된 휴직(ACTIVE)을 일일 근태에 반영
 * 근태 마감 DRAFT 배치에서 호출, 해당일은 AttendanceStatus.LEAVE 로 맞춤
 */
@Slf4j
@Component
public class LeaveOfAbsenceAttendanceReflector {

    private final MemberLeaveOfAbsenceRepository memberLeaveOfAbsenceRepository;
    private final DailyAttendanceRepository dailyAttendanceRepository;

    @Autowired
    public LeaveOfAbsenceAttendanceReflector(
            MemberLeaveOfAbsenceRepository memberLeaveOfAbsenceRepository,
            DailyAttendanceRepository dailyAttendanceRepository) {
        this.memberLeaveOfAbsenceRepository = memberLeaveOfAbsenceRepository;
        this.dailyAttendanceRepository = dailyAttendanceRepository;
    }

    @Transactional
    public int reflectForDate(LocalDate targetDate) {
        List<MemberLeaveOfAbsence> active =
                memberLeaveOfAbsenceRepository.findAllActiveOnDate(targetDate);

        int count = 0;
        for (MemberLeaveOfAbsence loa : active) {
            try {
                applyLeaveOfAbsenceToAttendance(loa, targetDate);
                count++;
            } catch (Exception e) {
                log.error("[LoaReflect] 반영 실패 leaveOfAbsenceId={}, date={}",
                        loa.getLeaveOfAbsenceId(), targetDate, e);
            }
        }
        log.info("[LoaReflect] 완료 date={}, count={}", targetDate, count);
        return count;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void applyLeaveOfAbsenceToAttendance(MemberLeaveOfAbsence loa, LocalDate targetDate) {
        AttendanceStatus newStatus = AttendanceStatus.LEAVE;

        Optional<DailyAttendance> existing = dailyAttendanceRepository
                .findByMemberIdAndAttendanceDate(loa.getMemberId(), targetDate);

        if (existing.isPresent()) {
            existing.get().updateStatus(newStatus);
        } else {
            DailyAttendance created = DailyAttendance.builder()
                    .memberId(loa.getMemberId())
                    .companyId(loa.getCompanyId())
                    .attendanceDate(targetDate)
                    .status(newStatus)
                    .workedMinutes(0)
                    .overtimeMinutes(0)
                    .build();
            dailyAttendanceRepository.save(created);
            log.debug("[LoaReflect] 신규 휴직 근태 생성 memberId={}, date={}",
                    loa.getMemberId(), targetDate);
        }
    }
}
