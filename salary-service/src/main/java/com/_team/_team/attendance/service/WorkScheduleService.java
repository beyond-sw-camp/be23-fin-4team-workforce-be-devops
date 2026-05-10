package com._team._team.attendance.service;

import com._team._team.attendance.publisher.RagSyncAttendanceEventPublisher;
import com._team._team.attendance.domain.enums.WorkType;
import com._team._team.attendance.repository.WorkScheduleRepository;
import com._team._team.attendance.domain.WorkSchedule;
import com._team._team.attendance.dto.reqDto.WorkScheduleCreateReqDto;
import com._team._team.attendance.dto.reqDto.WorkScheduleUpdateReqDto;
import com._team._team.attendance.dto.resDto.WorkScheduleResDto;
import com._team._team.dto.BusinessException;
import com._team._team.event.RagSyncAttendanceEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class WorkScheduleService {

    private final WorkScheduleRepository workScheduleRepository;
    private final RagSyncAttendanceEventPublisher ragSyncAttendanceEventPublisher;

    @Autowired
    public WorkScheduleService(WorkScheduleRepository workScheduleRepository,
                               RagSyncAttendanceEventPublisher ragSyncAttendanceEventPublisher) {
        this.workScheduleRepository = workScheduleRepository;
        this.ragSyncAttendanceEventPublisher = ragSyncAttendanceEventPublisher;
    }

    /** 스케줄 생성 */
    public WorkScheduleResDto createSchedule(UUID companyId, WorkScheduleCreateReqDto reqDto) {

        if (reqDto.getWorkType() == WorkType.FIXED) {
            if (reqDto.getStartTime() == null
                    || reqDto.getEndTime() == null
                    || reqDto.getWorkMinutes() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "고정 근무제는 출퇴근 시각·근무시간이 필수입니다.");
            }
        }

        // 운영 기간 중복 체크 - 같은 회사에 적용 기간이 겹치는 활성 정책 못만듬
        validateNoOverlap(companyId, null, reqDto.getEffectiveFrom(), reqDto.getEffectiveTo());

        // 월 단위 적용 강제 - 유연근무제 스케줄 선택 사이클이 월 단위라 중간 변경 시 직원 혼란스럽
        validateMonthlyBoundary(reqDto.getEffectiveFrom(), reqDto.getEffectiveTo());

        WorkSchedule workSchedule = reqDto.toEntity(companyId);
        WorkSchedule saveWorkSchedule = workScheduleRepository.save(workSchedule);

        ragSyncAttendanceEventPublisher.publish(
                RagSyncAttendanceEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("CREATED")
                        .resourceType("WORK_SCHEDULE")
                        .resourceId(saveWorkSchedule.getWorkScheduleId())
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );

        return WorkScheduleResDto.fromEntity(saveWorkSchedule);
    }

    /** 스케줄 목록 조회 (회사별) */
    @Transactional(readOnly = true)
    public List<WorkScheduleResDto> findSchedules(UUID companyId) {
        return workScheduleRepository.findByCompanyIdAndDelYn(companyId, "N")
                .stream().map(WorkScheduleResDto::fromEntity).toList();
    }

    /** 스케줄 단건 조회 */
    @Transactional(readOnly = true)
    public WorkScheduleResDto findSchedule(UUID companyId, UUID scheduleId) {
        WorkSchedule workSchedule = workScheduleRepository
                .findByCompanyIdAndWorkScheduleIdAndDelYn(companyId, scheduleId, "N")
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "근무 스케줄을 찾을 수 없습니다."));
        return WorkScheduleResDto.fromEntity(workSchedule);
    }

    /** 스케줄 수정 */
    public WorkScheduleResDto updateSchedule(UUID companyId, UUID scheduleId, WorkScheduleUpdateReqDto reqDto) {
        WorkSchedule workSchedule = workScheduleRepository
                .findByCompanyIdAndWorkScheduleIdAndDelYn(companyId, scheduleId, "N")
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "근무 스케줄을 찾을 수 없습니다."));

        /*
         * 결과 workType 이 FIXED 가 될 경우, 시간 정보가 not null 이어야 함
         */
        WorkType resultingWorkType =
                reqDto.getWorkType() != null ? reqDto.getWorkType() : workSchedule.getWorkType();
        if (resultingWorkType == WorkType.FIXED) {
            LocalTime resultStart =
                    reqDto.getStartTime() != null ? reqDto.getStartTime() : workSchedule.getStartTime();
            LocalTime resultEnd =
                    reqDto.getEndTime() != null ? reqDto.getEndTime() : workSchedule.getEndTime();
            Integer resultMinutes =
                    reqDto.getWorkMinutes() != null ? reqDto.getWorkMinutes() : workSchedule.getWorkMinutes();
            if (resultStart == null || resultEnd == null || resultMinutes == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "고정 근무제는 출퇴근 시각·근무시간이 필수입니다.");
            }
        }

        // 기간 변경 시 중복 체크 - 자기 자신 제외
        LocalDate newFrom = reqDto.getEffectiveFrom() != null ? reqDto.getEffectiveFrom() : workSchedule.getEffectiveFrom();
        LocalDate newTo = reqDto.getEffectiveTo() != null ? reqDto.getEffectiveTo() : workSchedule.getEffectiveTo();
        validateNoOverlap(companyId, scheduleId, newFrom, newTo);
        validateMonthlyBoundary(newFrom, newTo);

        workSchedule.update(reqDto);

        ragSyncAttendanceEventPublisher.publish(
                RagSyncAttendanceEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("UPDATED")
                        .resourceType("WORK_SCHEDULE")
                        .resourceId(scheduleId)
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );

        return WorkScheduleResDto.fromEntity(workSchedule);
    }

    /**
     * 운영 기간 중복 검증 - 같은 회사에 적용기간이 겹치는 활성 정책이 있으면 차단
     */
    private void validateNoOverlap(UUID companyId, UUID excludeId,
                                   LocalDate newFrom, LocalDate newTo) {
        if (newFrom == null) return;
        List<WorkSchedule> activeList = workScheduleRepository.findByCompanyIdAndDelYn(companyId, "N");
        for (WorkSchedule existing : activeList) {
            if (excludeId != null && excludeId.equals(existing.getWorkScheduleId())) continue;
            // 회사 단위 정책만 체크 - 개인스케줄은 제외
            if (existing.getMemberId() != null) continue;
            LocalDate exFrom = existing.getEffectiveFrom();
            LocalDate exTo = existing.getEffectiveTo();
            if (exFrom == null) continue;
            // 무기한
            boolean newStartsBeforeExEnd = exTo == null || !newFrom.isAfter(exTo);
            boolean exStartsBeforeNewEnd = newTo == null || !exFrom.isAfter(newTo);
            if (newStartsBeforeExEnd && exStartsBeforeNewEnd) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        String.format("운영 기간이 겹치는 활성 정책이 이미 있습니다 (%s ~ %s). 기존 정책을 먼저 종료하세요.",
                                exFrom,
                                exTo == null ? "진행중" : exTo.toString()));
            }
        }
    }

    /**
     * 적용 기간 월 단위 강제 - 시작일은 매월 1일, 종료일은 매월 말일로 바꿈
     */
    private void validateMonthlyBoundary(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveFrom != null && effectiveFrom.getDayOfMonth() != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "근무 스케줄 적용 시작일은 매월 1일만 가능합니다.");
        }
        if (effectiveTo != null) {
            LocalDate lastDay = effectiveTo.withDayOfMonth(effectiveTo.lengthOfMonth());
            if (!effectiveTo.equals(lastDay)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "근무 스케줄 적용 종료일은 매월 말일만 가능합니다. (예: 5월 종료라면 5/31)");
            }
        }
    }

    /** 스케줄 삭제 */
    public void deleteSchedule(UUID companyId, UUID scheduleId) {
        WorkSchedule workSchedule = workScheduleRepository
                .findByCompanyIdAndWorkScheduleIdAndDelYn(companyId, scheduleId, "N")
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "근무 스케줄을 찾을 수 없습니다."));
        workSchedule.delete();

        ragSyncAttendanceEventPublisher.publish(
                RagSyncAttendanceEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("DELETED")
                        .resourceType("WORK_SCHEDULE")
                        .resourceId(scheduleId)
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );
    }
}
