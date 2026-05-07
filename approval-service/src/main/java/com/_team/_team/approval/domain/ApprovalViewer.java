package com._team._team.approval.domain;

import com._team._team.approval.domain.enums.ViewerReadStatus;
import com._team._team.approval.domain.enums.ViewerType;
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
public class ApprovalViewer extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false)
    private UUID viewerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private ApprovalRequest approvalRequest;

    @Column(nullable = false)
    private UUID viewerMemberId;

    @Column(nullable = false)
    private UUID viewerMemberPositionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ViewerType viewerType; // CC(참조), CIRCULATION(공람)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ViewerReadStatus viewerReadStatus; // UNREAD(미확인), READ(확인 완료)

    private LocalDateTime viewedAt; // 사용자가 실제 문서 확인 시각

    public void markAsRead() {
        this.viewerReadStatus = ViewerReadStatus.READ;
        this.viewedAt = LocalDateTime.now();
    }
}
