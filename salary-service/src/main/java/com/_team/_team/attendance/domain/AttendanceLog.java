package com._team._team.attendance.domain;

import com._team._team.attendance.domain.enums.EventType;
import com._team._team.attendance.domain.enums.SourceType;
import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 출퇴근 이벤트 로그
 *  모든 근태 이벤트를 시간순으로 기록 이벤트 소싱
 *  daily_attendance 자식 테이블 N 대 1
 *  is_corrected_yn 결재 통한 수정 건 구분
 *
 * 인덱스 전략
 *  member_id event_time 특정 직원 기간별 로그 조회 빈번
 *  daily_attendance_id 일별 상세 로그 JOIN
 */
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class AttendanceLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID attendanceLogId;

    /** 소속 일별 근태 - LAZY로 불필요한 JOIN 방지 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_attendance_id", nullable = false)
    private DailyAttendance dailyAttendance;

    @Column(nullable = false)
    private UUID memberId;

    /** 이벤트 유형 (CLOCK_IN/CLOCK_OUT/BREAK_START/BREAK_END) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;

    /** 이벤트 발생 시각 - 인덱스 포함 컬럼 */
    @Column(nullable = false)
    private LocalDateTime eventTime;

    /** 기기 식별자 (모바일 앱에서 전송) */
    @Column(length = 100)
    private String deviceId;

    /** 이벤트 발생 채널 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SourceType sourceType = SourceType.WEB;

    /** 수정 여부 (N: 원본 / Y: 결재 통해 수정됨) */
    @Column(length = 1, nullable = false)
    @Builder.Default
    private String isCorrectedYn = "N";

    /** 수정 사유 (isCorrectedYn = 'Y' 일 때만 값 존재) */
    @Column(length = 100)
    private String correctionReason;

    /** 수정한 관리자 UUID (수기 수정, 대리 입력 시) */
    private UUID correctedBy;

    /** 수정 시각 */
    private LocalDateTime correctedAt;

    /** 관리자 수정 기록 */
    public void markCorrected(UUID adminId, String reason){
        this.isCorrectedYn = "Y";
        this.correctionReason = reason;
        this.correctedBy = adminId;
        this.correctedAt = LocalDateTime.now();
    }

}
