package com._team._team.approval.domain;

import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Builder
@Entity
public class OfficialRecipient extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID recipientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private ApprovalRequest approvalRequest;

    @Column(nullable = false)
    private UUID recipientOrganizationId; // 수신 부서 ID

    @Column(nullable = false)
    private String recipientOrganizationName; // 수신 부서명 (스냅샷)
}
