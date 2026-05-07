package com._team._team.attendance.service;

import com._team._team.attendance.domain.FlexibleTimeSlot;
import com._team._team.attendance.domain.WorkSchedule;
import com._team._team.attendance.domain.enums.ScheduleApprovalStatus;
import com._team._team.attendance.domain.enums.WorkType;
import com._team._team.attendance.repository.FlexibleTimeSlotRepository;
import com._team._team.attendance.repository.MemberScheduleSelectionRepository;
import com._team._team.attendance.repository.WorkScheduleRepository;
import com._team._team.dto.ApiResponse;
import com._team._team.salary.feignClients.MemberFeignClient;
import com._team._team.salary.feignClients.dto.MemberResDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 스케줄 슬롯 선택 마감일 경과 시 기본 슬롯 자동 할당 서비스
 * 대상 월은 다음 달
 */
@Slf4j
@Service
public class SlotDeadlineAutoAssignService {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);
    private static final String ACTIVE = "Y";

    private final WorkScheduleRepository workScheduleRepository;
    private final FlexibleTimeSlotRepository flexibleTimeSlotRepository;
    private final MemberScheduleSelectionRepository memberScheduleSelectionRepository;
    private final MemberScheduleSelectionService memberScheduleSelectionService;
    private final MemberFeignClient memberFeignClient;

    @Autowired
    public SlotDeadlineAutoAssignService(
            WorkScheduleRepository workScheduleRepository,
            FlexibleTimeSlotRepository flexibleTimeSlotRepository,
            MemberScheduleSelectionRepository memberScheduleSelectionRepository,
            MemberScheduleSelectionService memberScheduleSelectionService,
            MemberFeignClient memberFeignClient) {
        this.workScheduleRepository = workScheduleRepository;
        this.flexibleTimeSlotRepository = flexibleTimeSlotRepository;
        this.memberScheduleSelectionRepository = memberScheduleSelectionRepository;
        this.memberScheduleSelectionService = memberScheduleSelectionService;
        this.memberFeignClient = memberFeignClient;
    }

    /**
     * targetDate 기준 마감일 경과 자동 할당 실행
     * 오늘이 4/26 이면 마감일이 25일 인 회사 대상, 5월 슬롯 할당
     */
    @Transactional
    public AssignResult runForDate(LocalDate targetDate) {
        // 오늘이 어제인 회사만 대상
        int deadlineDay = targetDate.minusDays(1).getDayOfMonth();

        List<WorkSchedule> targetSchedules = workScheduleRepository
                .findActiveFlexibleWithDeadlineDay(deadlineDay);

        // 다음 달 YYYY-MM
        String targetYearMonth = YearMonth.from(targetDate).plusMonths(1).format(YM);

        int assignedCount = 0;

        for (WorkSchedule schedule : targetSchedules) {
            assignedCount += assignForSchedule(schedule, targetYearMonth);
        }

        log.info("[SlotDeadline] targetDate={} 대상스케줄={} 할당={}",
                targetDate, targetSchedules.size(), assignedCount);

        return new AssignResult(targetSchedules.size(), assignedCount);
    }

    // 특정 스케줄의 기본 스케줄 슬롯을 미선택 직원에게 자동 할당
    private int assignForSchedule(WorkSchedule schedule, String targetYearMonth) {
        FlexibleTimeSlot defaultSlot = flexibleTimeSlotRepository
                .findByWorkScheduleIdAndIsDefaultAndActiveYn(
                        schedule.getWorkScheduleId(), true, ACTIVE)
                .orElse(null);

        if (defaultSlot == null) {
            log.warn("[SlotDeadline] 기본 스케줄 슬롯 없음 workScheduleId={}",
                    schedule.getWorkScheduleId());
            return 0;
        }

        // 이미 해당 월에 유효 선택 있는 직원 목록
        Set<UUID> alreadySelected = new HashSet<>(
                memberScheduleSelectionRepository.findMembersWithActiveSelection(
                        schedule.getCompanyId(), targetYearMonth));

        // member-service Feign 으로 회사 재직 사원 목록 조회
        List<UUID> allMembers = fetchActiveMemberIds(schedule.getCompanyId());

        int assigned = 0;
        for (UUID memberId : allMembers) {
            if (alreadySelected.contains(memberId)) continue;

            memberScheduleSelectionService.autoAssignDefault(
                    schedule.getCompanyId(),
                    memberId,
                    targetYearMonth,
                    defaultSlot.getSlotId(),
                    SYSTEM_ACTOR);
            assigned++;
        }

        return assigned;
    }

    // member-service 호출, 실패 시 빈 리스트로 배치 안전 종료
    private List<UUID> fetchActiveMemberIds(UUID companyId) {
        try {
            ApiResponse<List<MemberResDto>> response = memberFeignClient.getMembersByCompany(companyId);
            if (response == null || response.getData() == null) {
                return Collections.emptyList();
            }
            return response.getData().stream()
                    .map(MemberResDto::getMemberId)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.error("[SlotDeadline] member-service 조회 실패 companyId={}", companyId, e);
            return Collections.emptyList();
        }
    }

    public record AssignResult(int targetSchedules, int assignedMembers) {}
}
