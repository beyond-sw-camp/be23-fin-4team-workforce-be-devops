package com._team._team.approval.domain;

import com._team._team.approval.domain.enums.LineStatus;
import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Builder
@Entity
public class Approval extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false)
    private UUID approvalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private ApprovalRequest request;

    @Column(nullable = false)
    private UUID approverMemberPositionId;

    @Column(nullable = false)
    private UUID approverMemberId;

    // --- 대결 관련 필드 ---
    @Column
    private UUID actualApproverMemberId;      // 실제 결재한 사람 (대결자)

    @Column
    private UUID actualApproverMemberPositionId;  // 실제 결재한 사람의 직위

    @Builder.Default
    @Column(columnDefinition = "varchar(1) default 'N'")
    private String isProxyYn = "N";   // 대리결재 여부

    @Column(nullable = false)
    private Integer stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LineStatus approvalStatus;

    private LocalDateTime actedAt;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(columnDefinition = "TEXT")
    private String signatureImageUrl;

    @Builder.Default
    @Column(nullable = false)
    private String isSignedYn = "N";

    public void cancel() {
        this.approvalStatus = LineStatus.CANCELED;
        this.actedAt = LocalDateTime.now();
    }

    public void sign(String signatureImageUrl) {
        this.isSignedYn = "Y";
        this.signatureImageUrl = signatureImageUrl;
    }

    public void activate() {
        this.approvalStatus = LineStatus.PENDING;
    }

    public void approve(String comment, UUID actualMemberId, UUID actualMemberPositionId) {
        this.approvalStatus = LineStatus.APPROVED;
        this.comment = comment;
        this.actedAt = LocalDateTime.now();
        this.actualApproverMemberId = actualMemberId;
        this.actualApproverMemberPositionId = actualMemberPositionId;
        if (actualMemberId != null) {
            this.isProxyYn = actualMemberId.equals(this.approverMemberId) ? "N" : "Y";
        }
    }

    public void reject(String comment, UUID actualMemberId, UUID actualMemberPositionId) {
        this.approvalStatus = LineStatus.REJECTED;
        this.comment = comment;
        this.actedAt = LocalDateTime.now();
        this.actualApproverMemberId = actualMemberId;
        this.actualApproverMemberPositionId = actualMemberPositionId;
        if (actualMemberId != null) {
            this.isProxyYn = actualMemberId.equals(this.approverMemberId) ? "N" : "Y";
        }
    }

}

