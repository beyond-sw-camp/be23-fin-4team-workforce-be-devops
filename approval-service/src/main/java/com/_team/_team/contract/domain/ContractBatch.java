package com._team._team.contract.domain;

import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Builder
@Entity
public class ContractBatch extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false)
    private UUID batchId;

    @Column(nullable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ContractTemplate contractTemplate;

    @Column(nullable = false)
    private String batchName;

    @Column(nullable = false)
    private Integer totalCount;

    @Builder.Default
    @Column(nullable = false)
    private Integer signedCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer rejectedCount = 0;

    @Column(nullable = false)
    private UUID createdBy;

    @Column
    private UUID previousBatchId;

    public void incrementSignedCount() {
        this.signedCount++;
    }

    public void incrementRejectedCount() {
        this.rejectedCount++;
    }
}
