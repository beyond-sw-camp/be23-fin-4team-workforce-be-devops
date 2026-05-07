package com._team._team.esg.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.esg.domain.enums.ActivityStatus;
import com._team._team.esg.domain.enums.EsgCategory;
import com._team._team.member.domain.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "esg_activity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EsgActivity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID esgActivityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
            nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "esg_activity_subject_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
            nullable = false)
    private EsgActivitySubject subject;

    // 집계 쿼리 성능을 위해 category 비정규화
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EsgCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ActivityStatus status = ActivityStatus.PENDING;

    // 텍스트 내용
    @Column(columnDefinition = "TEXT")
    private String verificationContent;

    // 첨부 파일 S3 URL (이미지, PDF 등)
    private String fileUrl;

    // 승인 시점에 결정
    private Integer earnedPoints;

    // 승인/반려한 관리자 memberId
    private UUID approvedBy;

    private LocalDateTime approvedAt;

    private String rejectReason;

    // 도메인 메서드
    public void approve(UUID approverId, int points) {
        this.status       = ActivityStatus.APPROVED;
        this.approvedBy   = approverId;
        this.approvedAt   = LocalDateTime.now();
        this.earnedPoints = points;
    }

    public void reject(UUID approverId, String reason) {
        this.status       = ActivityStatus.REJECTED;
        this.approvedBy   = approverId;
        this.rejectReason = reason;
    }

    public boolean isPending() { return this.status == ActivityStatus.PENDING; }
    public boolean isOwner(UUID memberId) { return this.member.getMemberId().equals(memberId); }
}
