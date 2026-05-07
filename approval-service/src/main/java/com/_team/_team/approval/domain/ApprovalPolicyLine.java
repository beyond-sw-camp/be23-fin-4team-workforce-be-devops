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
public class ApprovalPolicyLine extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false)
    private UUID policyLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name ="document_id", nullable = false)
    private ApprovalDocument approvalDocument;

    @Column(nullable = false)
    private UUID jobTitleId;

    @Column(nullable = false)
    private Integer stepOrder;

    private UUID organizationId;

    @Column(nullable = false)
    @Builder.Default
    private String delYn = "N";

    public void approvalPolicyLineDelete() {
        this.delYn = "Y";
    }


}
