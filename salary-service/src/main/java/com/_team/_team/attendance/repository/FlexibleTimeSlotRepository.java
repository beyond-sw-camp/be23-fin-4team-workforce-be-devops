package com._team._team.attendance.repository;

import com._team._team.attendance.domain.FlexibleTimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlexibleTimeSlotRepository extends JpaRepository<FlexibleTimeSlot, UUID> {

    // 스케줄에 속한 활성 슬롯 전체 (선택 UI용)
    List<FlexibleTimeSlot> findAllByWorkScheduleIdAndActiveYn(UUID workScheduleId, String activeYn);

    // 기본 슬롯 1건 조회 (신규 입사자 자동 할당용)
    Optional<FlexibleTimeSlot> findByWorkScheduleIdAndIsDefaultAndActiveYn(
            UUID workScheduleId, Boolean isDefault, String activeYn);

    // 슬롯 코드 중복 체크 (생성 시 검증)
    boolean existsByWorkScheduleIdAndSlotCode(UUID workScheduleId, String slotCode);

    long countByWorkScheduleIdAndActiveYn(UUID workScheduleId, String activeYn);

    // 같은 스케줄 내 시작/종료 시간 중복 체크
    boolean existsByWorkScheduleIdAndStartTimeAndEndTimeAndActiveYn(
            UUID workScheduleId,
            LocalTime startTime,
            LocalTime endTime,
            String activeYn
    );

    boolean existsByWorkScheduleIdAndStartTimeAndEndTimeAndActiveYnAndSlotIdNot(
            UUID workScheduleId,
            LocalTime startTime,
            LocalTime endTime,
            String activeYn,
            UUID slotId
    );

    // 회사 범위 내에서만 조회
    Optional<FlexibleTimeSlot> findBySlotIdAndCompanyId(UUID slotId, UUID companyId);

    // 기본 슬롯 교체 시 기존 default 조회
    @Query("SELECT s FROM FlexibleTimeSlot s " +
            "WHERE s.workScheduleId = :workScheduleId AND s.isDefault = true")
    Optional<FlexibleTimeSlot> findCurrentDefault(UUID workScheduleId);
}
