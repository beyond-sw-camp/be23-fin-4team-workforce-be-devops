package com._team._team.attendance.domain;

import com._team._team.attendance.domain.enums.WorkType;
import com._team._team.attendance.dto.reqDto.WorkScheduleUpdateReqDto;
import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * 근무 스케줄
 * - memberId가 null이면 회사 전체 기본 스케줄
 * - memberId가 있으면 개인별 스케줄 (우선 적용)
 * - effectiveFrom/To로 기간별 스케줄 이력 관리
 * - workMinutes: overtime 계산의 기준값
 */

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class WorkSchedule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID workScheduleId;

    @Column(nullable = false)
    private UUID companyId;

    private UUID memberId;

    @Column(nullable = false, length = 100)
    private String scheduleName;

    /** 근무 유형 (FIXED: 고정 / FLEXIBLE: 유연 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkType workType;

    /**
     * 규정 출근 시각.
     * - FIXED: 필수
     * - FLEXIBLE: null (실제 시간대는 FlexibleTimeSlot 에서 정의)
     */
    @Column
    private LocalTime startTime;

    /**
     * 규정 퇴근 시각.
     * - FIXED: 필수
     * - FLEXIBLE: null
     */
    @Column
    private LocalTime endTime;

    /**
     * 하루 규정 근무시간(분) - overtime 계산 기준.
     * - FIXED: 필수
     * - FLEXIBLE: null (슬롯의 workMinutes 사용)
     */
    @Column
    private Integer workMinutes;

    /**
     * 회사가 정한 점심·휴게 시작 시각 (예: 12:00).
     * - FIXED: 모든 직원에게 일률 적용 (예: 12:00 ~ 13:00)
     * - FLEXIBLE: 사용 안 함 (직원이 매월 슬롯 선택 시 MemberScheduleSelection 에 본인 점심 시작/종료를 정함)
     */
    @Column
    private LocalTime breakStart;

    /** 회사가 정한 점심·휴게 종료 시각 (예: 13:00) */
    @Column
    private LocalTime breakEnd;

    /**
     * 슬롯 선택 마감일 (매월 며칠까지 다음 달 슬롯을 선택해야 하는지)
     * FLEXIBLE 스케줄에만 적용
     * 예: 25 이면 전월 25일까지 다음 달 슬롯 선택 마감
     * 이 날짜가 지나면 배치가 기본 슬롯으로 자동 할당 (ScheduleApprovalStatus=AUTO)
     */
    @Column
    private Integer selectionDeadlineDay;

    /** 스케줄 시작일 */
    @Column(nullable = false)
    private LocalDate effectiveFrom;

    /** 스케줄 종료일 (null이면 현재 적용중) */
    private LocalDate effectiveTo;

    @Column(length = 1, nullable = false)
    @Builder.Default
    private String delYn = "N";

    public void update(WorkScheduleUpdateReqDto dto){
        if (dto.getScheduleName() != null) this.scheduleName = dto.getScheduleName();
        if (dto.getWorkType() != null) this.workType = dto.getWorkType();

        /*
         * 시간 필드는 최종 workType 에 따라 결정
         */
        if (this.workType == WorkType.FLEXIBLE) {
            this.startTime = null;
            this.endTime = null;
            this.workMinutes = null;
        } else {
            if (dto.getStartTime() != null) this.startTime = dto.getStartTime();
            if (dto.getEndTime() != null) this.endTime = dto.getEndTime();
            if (dto.getWorkMinutes() != null) this.workMinutes = dto.getWorkMinutes();
        }

        // FLEXIBLE 은 점심을 직원이 매월 정하므로 회사 정책 점심 시각은 비움.
        if (this.workType == WorkType.FLEXIBLE) {
            this.breakStart = null;
            this.breakEnd = null;
        } else {
            if (dto.getBreakStart() != null) this.breakStart = dto.getBreakStart();
            if (dto.getBreakEnd() != null) this.breakEnd = dto.getBreakEnd();
        }
        if (dto.getSelectionDeadlineDay() != null) this.selectionDeadlineDay = dto.getSelectionDeadlineDay();
        if (dto.getEffectiveFrom() != null) this.effectiveFrom = dto.getEffectiveFrom();
        if (dto.getEffectiveTo() != null) this.effectiveTo = dto.getEffectiveTo();
    }

    /**
     * breakStart/breakEnd 로부터 점심·휴게 시간(분) 산정.
     * 시작이 종료보다 늦으면 자정을 넘긴 것으로 보고 +24h 보정.
     * 둘 중 하나라도 null 이면 0 반환.
     */
    public int computeBreakMinutes() {
        if (breakStart == null || breakEnd == null) return 0;
        Duration d = Duration.between(breakStart, breakEnd);
        long m = d.toMinutes();
        if (m < 0) m += 24 * 60;
        return (int) Math.max(0, m);
    }

    public void delete(){
        this.delYn = "Y";
    }
}
