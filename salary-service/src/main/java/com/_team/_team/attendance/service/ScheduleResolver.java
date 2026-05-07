package com._team._team.attendance.service;
import com._team._team.attendance.domain.FlexibleTimeSlot;
import com._team._team.attendance.domain.MemberScheduleSelection;
import com._team._team.attendance.domain.WorkSchedule;
import com._team._team.attendance.domain.enums.WorkType;
import com._team._team.attendance.dto.vo.ResolvedSchedule;
import com._team._team.attendance.repository.FlexibleTimeSlotRepository;
import com._team._team.attendance.repository.MemberScheduleSelectionRepository;
import com._team._team.attendance.repository.WorkScheduleRepository;
import com._team._team.dto.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 스케줄 결정
 * 한 직원의 특정 일자에 적용되는 최종 근무 스케줄을 결정
 *
 * <우선순위>
 * 1. 개인 WorkSchedule (memberId 지정된 것)
 * 2. 회사 기본 WorkSchedule (memberId IS NULL)
 * 3. FLEXIBLE이면 슬롯 결정
 */
@Service
@Transactional
public class ScheduleResolver {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String ACTIVE = "Y";
    private static final String NOT_DELETED = "N";

    private final WorkScheduleRepository workScheduleRepository;
    private final FlexibleTimeSlotRepository flexibleTimeSlotRepository;
    private final MemberScheduleSelectionRepository memberScheduleSelectionRepository;

    @Autowired
    public ScheduleResolver(WorkScheduleRepository workScheduleRepository, FlexibleTimeSlotRepository flexibleTimeSlotRepository, MemberScheduleSelectionRepository memberScheduleSelectionRepository) {
        this.workScheduleRepository = workScheduleRepository;
        this.flexibleTimeSlotRepository = flexibleTimeSlotRepository;
        this.memberScheduleSelectionRepository = memberScheduleSelectionRepository;
    }

    /**
     * 특정 직원, 특정 일자의 최종 스케줄 결정
     */
    public ResolvedSchedule resolve(UUID memberId, UUID companyId, LocalDate date) {

        // 1. 개인 스케줄 우선 확인
        WorkSchedule personal = workScheduleRepository
                .findEffectivePersonal(memberId, date)
                .orElse(null);

        if (personal != null) {
            return buildResolved(personal, memberId, date);
        }

        // 2. 회사 기본 스케줄 조회 (없으면 예외)
        WorkSchedule company = workScheduleRepository
                .findEffectiveCompany(companyId, date)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "적용할 근무 스케줄이 없습니다."));

        return buildResolved(company, memberId, date);
    }

    // WorkType에 따라 FIXED 또는 FLEXIBLE로 분기 조립
    private ResolvedSchedule buildResolved(WorkSchedule schedule,
                                           UUID memberId,
                                           LocalDate date) {
        if (schedule.getWorkType() == WorkType.FIXED) {
            return ResolvedSchedule.fromFixed(
                    schedule.getWorkScheduleId(),
                    schedule.getStartTime(),
                    schedule.getEndTime(),
                    schedule.getWorkMinutes(),
                    schedule.computeBreakMinutes(),
                    schedule.getBreakStart(),
                    schedule.getBreakEnd()
            );
        }

        // FLEXIBLE인 경우 슬롯 결정
        return resolveFlexible(schedule, memberId, date);
    }

    // FLEXIBLE 스케줄에서 슬롯 결정
    private ResolvedSchedule resolveFlexible(WorkSchedule schedule,
                                             UUID memberId,
                                             LocalDate date) {
        String yearMonth = date.format(YM);

        // 3. 이번 달 유효한 선택(APPROVED 또는 AUTO) 중 최신 건 조회
        MemberScheduleSelection selection = memberScheduleSelectionRepository
                .findCurrentActive(memberId, yearMonth)
                .orElse(null);

        UUID slotId;
        Integer memberSelectedBreak = null;
        if (selection != null) {
            slotId = selection.getSlotId();
            memberSelectedBreak = selection.computeBreakMinutes(); // 직원이 선택한 점심시간 시작/종료 → 분
        } else {
            // 4. 선택 없으면 기본 슬롯
            FlexibleTimeSlot defaultSlot = flexibleTimeSlotRepository
                    .findByWorkScheduleIdAndIsDefaultAndActiveYn(
                            schedule.getWorkScheduleId(), true, ACTIVE)
                    .orElseThrow(() -> new BusinessException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "시차출퇴근제 스케줄이 없습니다."));
            slotId = defaultSlot.getSlotId();
        }

        FlexibleTimeSlot slot = flexibleTimeSlotRepository
                .findById(slotId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "시차출퇴근제 스케줄이 등록되어있지 않습니다."));

        int breakMinutes = memberSelectedBreak != null ? memberSelectedBreak : 0;

        // 휴게 시각 - 직원 선택이 있으면 그것 사용, 없으면 슬롯 기본값
        LocalTime breakStart = selection != null && selection.getBreakStart() != null
                ? selection.getBreakStart()
                : slot.getBreakStart();
        LocalTime breakEnd = selection != null && selection.getBreakEnd() != null
                ? selection.getBreakEnd()
                : slot.getBreakEnd();

        return ResolvedSchedule.fromFlexible(
                schedule.getWorkScheduleId(),
                slot.getSlotId(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getWorkMinutes(),
                breakMinutes,
                breakStart,
                breakEnd
        );
    }
}