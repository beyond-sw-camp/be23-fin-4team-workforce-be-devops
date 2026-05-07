package com._team._team.attendance.domain;
import com._team._team.attendance.domain.enums.OvertimeApprovalStatus;
import com._team._team.attendance.domain.enums.OvertimeRequestType;
import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * 연장근로 신청
 * 전자결재 연동
 *  제출 시 approval-service에 결재 생성 요청
 *  결재 완료 이벤트(Kafka) 수신 시 approve()/reject()
 */
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Builder
@Getter
@Table(
        indexes = {
                // 일별 승인 OT 집계 (직원 단건)
                @Index(name = "idx_overtime_member_date_status",
                        columnList = "memberId, targetDate, approvalStatus"),

                // 결재 대기 목록 (관리자 승인 화면)
                @Index(name = "idx_overtime_company_status",
                        columnList = "companyId, approvalStatus"),

                // 회사 단위 월별 초과근무시간 누적 집계 (포괄임금제 초과 근무 현황)
                @Index(name = "idx_overtime_company_date_status",
                        columnList = "companyId, targetDate, approvalStatus"),

                // 사후 신청 만료 배치용
                @Index(name = "idx_overtime_type_status_submitted",
                        columnList = "requestType, approvalStatus, submittedAt")
        }
)
public class OvertimeRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID overtimeRequestId;

    @Column(nullable = false)
    private UUID memberId;

    @Column(nullable = false)
    private UUID companyId;

    /** 연장근로 대상 일자 */
    @Column(nullable = false)
    private LocalDate targetDate;

    /**
     * 신청 타입
     * PRE  사전 신청 (근무 전 미리)
     * POST 사후 신청 (실제 근무 후)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OvertimeRequestType requestType;

    // ========== 사전 신청용 필드 (PRE) ==========

    /** 예정 시작 시각 */
    private LocalTime plannedStartTime;

    /** 예정 종료 시각 */
    private LocalTime plannedEndTime;

    /** 신청 시간(분) */
    private Integer requestedMinutes;

    // ========== 사후 신청용 필드 (POST) ==========

    /** 실제 시작 시각 */
    private LocalTime actualStartTime;

    /** 실제 종료 시각 */
    private LocalTime actualEndTime;

    /** 실제 근무 시간(분) */
    private Integer actualMinutes;

    // ========== 공통 ==========

    /** 신청 사유 */
    @Column(length = 500)
    private String reason;

    /** 결재 상태 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OvertimeApprovalStatus approvalStatus = OvertimeApprovalStatus.PENDING;

    /**
     * 연동된 결재 요청 ID
     * approval-service 의 결재 건 UUID (두 서비스 간 연결고리)
     */
    private UUID approvalRequestId;

    /**
     * 최종 승인된 시간(분)
     * 부분 승인 가능 (신청 120 -> 승인 90)
     * 급여 계산은 이 값을 사용
     */
    private Integer approvedMinutes;

    /** 신청 제출 시각 */
    @Column(nullable = false)
    private LocalDateTime submittedAt;

    /** 승인 또는 반려 결정 시각 */
    private LocalDateTime decidedAt;

    /** 결정자 UUID (관리자) */
    private UUID decidedBy;

    /** 결정 코멘트 (반려 사유 등) */
    @Column(length = 500)
    private String decisionNote;

    // ===== 상태 전이 메서드 =====

    /** 승인 처리 (부분 승인 포함) */
    public void approve(UUID approverId, Integer approvedMinutes, LocalDateTime decidedAt) {
        if (this.approvalStatus != OvertimeApprovalStatus.PENDING) {
            throw new IllegalStateException("요청 상태에서만 승인할 수 있습니다.");
        }
        this.approvalStatus = OvertimeApprovalStatus.APPROVED;
        this.approvedMinutes = approvedMinutes;
        this.decidedBy = approverId;
        this.decidedAt = decidedAt;
    }

    /** 반려 처리 */
    public void reject(UUID approverId, LocalDateTime decidedAt, String note) {
        if (this.approvalStatus != OvertimeApprovalStatus.PENDING) {
            throw new IllegalStateException("요청 상태에서만 반려할 수 있습니다.");
        }
        this.approvalStatus = OvertimeApprovalStatus.REJECTED;
        this.decidedBy = approverId;
        this.decidedAt = decidedAt;
        this.decisionNote = note;
    }

    /** 취소 처리 (본인이 결재 전 철회) */
    public void cancel() {
        if (this.approvalStatus != OvertimeApprovalStatus.PENDING) {
            throw new IllegalStateException("요청 상태에서만 취소할 수 있습니다.");
        }
        this.approvalStatus = OvertimeApprovalStatus.CANCELLED;
    }

    /** 72시간 경과 자동 만료 (배치가 호출) */
    public void expire(LocalDateTime expiredAt) {
        if (this.approvalStatus != OvertimeApprovalStatus.PENDING) {
            return;
        }
        this.approvalStatus = OvertimeApprovalStatus.EXPIRED;
        this.decidedAt = expiredAt;
    }

    /** 결재 ID 연결 (approval-service에 결재 생성 후 회신) */
    public void linkApprovalRequest(UUID approvalRequestId) {
        this.approvalRequestId = approvalRequestId;
    }
}