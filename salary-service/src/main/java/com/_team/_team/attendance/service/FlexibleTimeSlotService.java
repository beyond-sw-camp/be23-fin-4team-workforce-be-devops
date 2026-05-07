package com._team._team.attendance.service;

import com._team._team.attendance.domain.FlexibleTimeSlot;
import com._team._team.attendance.dto.reqDto.FlexibleTimeSlotCreateReqDto;
import com._team._team.attendance.dto.reqDto.FlexibleTimeSlotUpdateReqDto;
import com._team._team.attendance.dto.resDto.FlexibleTimeSlotResDto;
import com._team._team.attendance.repository.FlexibleTimeSlotRepository;
import com._team._team.dto.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 시차출퇴근제 스케줄 관리 서비스
 * 관리자가 FLEXIBLE 스케줄 내의 슬롯 CRUD
 */
@Service
@Transactional
public class FlexibleTimeSlotService {

    private static final String ACTIVE = "Y";

    private final FlexibleTimeSlotRepository flexibleTimeSlotRepository;

    @Autowired
    public FlexibleTimeSlotService(FlexibleTimeSlotRepository flexibleTimeSlotRepository) {
        this.flexibleTimeSlotRepository = flexibleTimeSlotRepository;
    }

    /**
     * 시차 스케줄 생성
     */
    public FlexibleTimeSlotResDto create(UUID companyId,
                                         FlexibleTimeSlotCreateReqDto reqDto) {
        String resolvedSlotCode = resolveSlotCode(reqDto);
        boolean hasDefault = flexibleTimeSlotRepository
                .findByWorkScheduleIdAndIsDefaultAndActiveYn(reqDto.getWorkScheduleId(), true, ACTIVE)
                .isPresent();

        // 같은 스케줄 내 시간 중복 방지
        if (flexibleTimeSlotRepository.existsByWorkScheduleIdAndSlotCode(
                reqDto.getWorkScheduleId(), resolvedSlotCode)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "이미 존재하는 스케줄 코드입니다.");
        }
        if (flexibleTimeSlotRepository.existsByWorkScheduleIdAndStartTimeAndEndTimeAndActiveYn(
                reqDto.getWorkScheduleId(),
                reqDto.getStartTime(),
                reqDto.getEndTime(),
                ACTIVE
        )) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "이미 동일한 시간대의 스케줄이 있습니다.");
        }

        // 기본 스케줄 지정 요청 시 기존 기본 슬롯 자동 해제
        // 아직 기본 스케줄이 없으면 첫 등록을 자동 기본으로 지정
        boolean shouldBeDefault = Boolean.TRUE.equals(reqDto.getIsDefault()) || !hasDefault;
        if (shouldBeDefault) {
            unsetExistingDefault(reqDto.getWorkScheduleId());
        }

        FlexibleTimeSlot saved = flexibleTimeSlotRepository.save(
                FlexibleTimeSlot.builder()
                        .workScheduleId(reqDto.getWorkScheduleId())
                        .companyId(companyId)
                        .slotCode(resolvedSlotCode)
                        .slotLabel(reqDto.getSlotLabel())
                        .startTime(reqDto.getStartTime())
                        .endTime(reqDto.getEndTime())
                        .workMinutes(reqDto.getWorkMinutes())
                        .breakStart(reqDto.getBreakStart())
                        .breakEnd(reqDto.getBreakEnd())
                        .isDefault(shouldBeDefault)
                        .activeYn(ACTIVE)
                        .build()
        );
        return FlexibleTimeSlotResDto.fromEntity(saved);
    }

    /**
     * 스케줄 수정
     */
    public FlexibleTimeSlotResDto update(UUID slotId,
                                         UUID companyId,
                                         FlexibleTimeSlotUpdateReqDto reqDto) {
        FlexibleTimeSlot slot = findSlot(slotId, companyId);
        if (flexibleTimeSlotRepository.existsByWorkScheduleIdAndStartTimeAndEndTimeAndActiveYnAndSlotIdNot(
                slot.getWorkScheduleId(),
                reqDto.getStartTime(),
                reqDto.getEndTime(),
                ACTIVE,
                slotId
        )) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "이미 동일한 시간대의 스케줄이 있습니다.");
        }
        slot.update(
                reqDto.getSlotLabel(),
                reqDto.getStartTime(),
                reqDto.getEndTime(),
                reqDto.getWorkMinutes(),
                reqDto.getBreakStart(),
                reqDto.getBreakEnd()
        );
        return FlexibleTimeSlotResDto.fromEntity(slot);
    }

    /**
     * 스케줄 폐지 (소프트 삭제)
     */
    public void deactivate(UUID slotId, UUID companyId) {
        FlexibleTimeSlot slot = findSlot(slotId, companyId);
        if (Boolean.TRUE.equals(slot.getIsDefault())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "기본 스케줄은 삭제할 수 없습니다.");
        }
        long activeCount = flexibleTimeSlotRepository
                .countByWorkScheduleIdAndActiveYn(slot.getWorkScheduleId(), ACTIVE);
        if (activeCount <= 1) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "시차 스케줄은 최소 1개 이상 유지되어야 합니다.");
        }
        slot.deactivate();
    }

    /**
     * 기본 스케줄 지정
     * 기존 기본 스케줄 자동 해제 후 이 스케줄 기본으로 설정
     */
    public FlexibleTimeSlotResDto setAsDefault(UUID slotId, UUID companyId) {
        FlexibleTimeSlot target = findSlot(slotId, companyId);
        unsetExistingDefault(target.getWorkScheduleId());
        target.setAsDefault();
        return FlexibleTimeSlotResDto.fromEntity(target);
    }

    /**
     * 스케줄 단건 조회
     */
    @Transactional(readOnly = true)
    public FlexibleTimeSlotResDto findById(UUID slotId, UUID companyId) {
        return FlexibleTimeSlotResDto.fromEntity(findSlot(slotId, companyId));
    }

    /**
     * 특정 스케줄의 활성 슬롯 목록
     */
    @Transactional(readOnly = true)
    public List<FlexibleTimeSlotResDto> findActiveByWorkSchedule(UUID workScheduleId) {
        return flexibleTimeSlotRepository
                .findAllByWorkScheduleIdAndActiveYn(workScheduleId, ACTIVE)
                .stream()
                .map(FlexibleTimeSlotResDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 슬롯 찾기
    private FlexibleTimeSlot findSlot(UUID slotId, UUID companyId) {
        return flexibleTimeSlotRepository
                .findBySlotIdAndCompanyId(slotId, companyId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "슬롯을 찾을 수 없습니다."));
    }

    // 기존 기본 슬롯 해제
    private void unsetExistingDefault(UUID workScheduleId) {
        flexibleTimeSlotRepository
                .findByWorkScheduleIdAndIsDefaultAndActiveYn(workScheduleId, true, ACTIVE)
                .ifPresent(FlexibleTimeSlot::unsetDefault);
    }

    private String resolveSlotCode(FlexibleTimeSlotCreateReqDto reqDto) {
        String requested = reqDto.getSlotCode() == null ? "" : reqDto.getSlotCode().trim();
        if (!requested.isEmpty()) return requested;

        String base = buildSlotCodeBase(reqDto.getSlotLabel());
        String candidate = base;
        int seq = 2;
        while (flexibleTimeSlotRepository.existsByWorkScheduleIdAndSlotCode(reqDto.getWorkScheduleId(), candidate)) {
            candidate = withSuffix(base, seq);
            seq += 1;
        }
        return candidate;
    }

    private String buildSlotCodeBase(String label) {
        if (label == null || label.isBlank()) return "SCHEDULE";
        String normalized = label.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9가-힣]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (normalized.isBlank()) return "SCHEDULE";
        String strict = normalized.replaceAll("[^A-Z_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (strict.isBlank()) return "SCHEDULE";
        return strict.length() > 30 ? strict.substring(0, 30) : strict;
    }

    private String withSuffix(String base, int seq) {
        String suffix = "_S" + "X".repeat(Math.max(0, seq - 2));
        String candidate = base + suffix;
        return candidate.length() > 30 ? candidate.substring(0, 30) : candidate;
    }
}
