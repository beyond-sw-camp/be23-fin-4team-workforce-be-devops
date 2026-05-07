package com._team._team.attendance.domain;

import com._team._team.attendance.domain.enums.AttendanceStatus;
import com._team._team.attendance.domain.enums.ClosureStatus;
import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 일별 근태 집계
 * - attendance_log의 부모 테이블 (1:N)
 * - 월간 근태 조회 시 이 테이블만 조회
 */
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Getter
@Builder
@Table(
        indexes = {
                // 본인 월간 조회 (가장 자주 사용)
                @Index(name = "idx_daily_member_date",
                        columnList = "memberId, attendanceDate"),
                // 회사 일자별 전체 조회 (관리자 대시보드 / 배치 / 휴일 반영)
                @Index(name = "idx_daily_company_date",
                        columnList = "companyId, attendanceDate"),
                // 회사 + 마감 상태 (월 마감 배치)
                @Index(name = "idx_daily_company_closure",
                        columnList = "companyId, closureStatus")
        }
)
public class DailyAttendance extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID dailyAttendanceId;

    @Column(nullable = false)
    private UUID memberId;

    @Column(nullable = false)
    private UUID companyId;

    /** 근무일 */
    @Column(nullable = false)
    private LocalDate attendanceDate;

    /** 근태 상태 (NORMAL/ABSENT/LEAVE/HALF) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AttendanceStatus status = AttendanceStatus.NORMAL;

    /** 일 근태 마감 단계 (OPEN → DRAFT → UNDER_REVIEW → FINALIZED → LOCKED) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ClosureStatus closureStatus = ClosureStatus.OPEN;

    /** 적용된 근무 스케줄(overtime 계산 기준) */
    private UUID workScheduleId;

    /** 출근 시각 (하루 1회) */
    private LocalDateTime firstClockIn;

    /** 퇴근 시각 (하루 1회) */
    private LocalDateTime lastClockOut;

    /** 실 근무 시간(분) - (퇴근-출근) */
    private Integer workedMinutes;

    /** 초과 근무 시간(분) - workMinutes가 스케줄 workMinutes 초과 시 */
    private Integer overtimeMinutes;

    /**
     * 조퇴계 결재 승인 플래그 - 'Y' 면 실제 퇴근 시각이 정규 종료 시각보다 일러도
     * 정규 근무 시간을 schedule.workMinutes 그대로 인정
     */
    @Column(length = 1)
    @Builder.Default
    private String earlyLeaveExcusedYn = "N";

    /** 출퇴근 이벤트 로그 (상세 조회 시에만 LAZY 로딩) */
    @OneToMany(mappedBy = "dailyAttendance", fetch = FetchType.LAZY)
    @Builder.Default
    private List<AttendanceLog> logs = new ArrayList<>();

    /** 출장/외근 내역 */
    @OneToMany(mappedBy = "dailyAttendance", fetch = FetchType.LAZY)
    @Builder.Default
    private List<WorkTripDetail> trips = new ArrayList<>();

    //  집계 메서드

    /** 출근 기록 (하루 1회) */
    public void updateClockIn(LocalDateTime time) {
        this.firstClockIn = time;
    }

    /** 퇴근 기록 (하루 1회) */
    public void updateClockOut(LocalDateTime time) {
        this.lastClockOut = time;
    }

    /** 퇴근 취소 — 잘못 누른 경우 직원 본인이 OPEN 상태일 때만 호출 가능.
     *  lastClockOut 만 null 로 되돌리고 근무/연장 분도 초기화. */
    public void cancelClockOut() {
        this.lastClockOut = null;
        this.workedMinutes = 0;
        this.overtimeMinutes = 0;
    }

    /** 근무/초과 시간 재계산
     * 근무시간 = (퇴근 - 출근) - 휴게시간(분)
     */
    public void recalculateMinutes(int scheduleWorkMinutes, int breakMinutes){
        if(firstClockIn == null || lastClockOut == null) return;

        long totalMinutes = Duration.between(firstClockIn, lastClockOut).toMinutes();
        this.workedMinutes = (int) Math.max(0, totalMinutes - breakMinutes);

        if(this.workedMinutes > scheduleWorkMinutes){
            this.overtimeMinutes = this.workedMinutes - scheduleWorkMinutes;
        } else{
            this.overtimeMinutes = 0;
        }
    }

    /** 근태 상태 변경 (휴가 승인, 수동 수정 등) */
    public void updateStatus(AttendanceStatus status){
        this.status = status;
    }

    /** 일마감 단계 전이 */
    public void transitionClosure(ClosureStatus next){
        if (this.closureStatus == ClosureStatus.LOCKED) {
            throw new IllegalStateException("처리된 근태는 변경할 수 없습니다.");
        }
        this.closureStatus = next;
    }

    /**
     * WorkTimeClassifier 결과를 엔티티에 반영
     */
    public void applyWorkResult(int workedMinutes, int overtimeMinutes) {
        this.workedMinutes = workedMinutes;
        this.overtimeMinutes = overtimeMinutes;
    }

    /** 조퇴계 결재 승인 플래그 ON - 정규 근무 시간 면제용 */
    public void markEarlyLeaveExcused() {
        this.earlyLeaveExcusedYn = "Y";
    }

    /** 조퇴계 반려/취소 시 플래그 OFF */
    public void clearEarlyLeaveExcused() {
        this.earlyLeaveExcusedYn = "N";
    }

    public boolean isEarlyLeaveExcused() {
        return "Y".equals(this.earlyLeaveExcusedYn);
    }
}
