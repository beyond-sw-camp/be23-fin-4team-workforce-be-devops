package com._team._team.contract.domain;

import com._team._team.contract.domain.enums.ContractStatus;
import com._team._team.contract.domain.enums.ContractType;
import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Builder
@Entity
public class Contract extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false)
    private UUID contractId;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ContractTemplate contractTemplate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private ContractBatch contractBatch;

    @Column(nullable = false)
    private UUID employeeMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractType contractType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private String contentJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String formSchemaSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ContractStatus contractStatus = ContractStatus.CREATED;

    @Column
    private String signedPdfUrl;

    @Builder.Default
    @Column(nullable = false)
    private String delYn = "N";

    // 직원 스냅샷
    @Column(nullable = false)
    private String employeeName;

    @Column
    private String employeeSabun;

    @Column
    private String organizationName;

    @Column
    private String jobTitleName;

    private String rejectReason;

    // 재발송 이력 관련
    @Column
    private UUID previousContractId;

    @Builder.Default
    @Column(nullable = false)
    private Integer revision = 1;

    @Column
    private String cancelReason;

    @Column(columnDefinition = "TEXT")
    private String sealImageUrl;

    @Column(length = 30, unique = true)
    private String contractNumber;  // 계약 문서번호 (예: 근로-2026-0001)

    public void send() {
        this.contractStatus = ContractStatus.SENT;
    }

    public void sign() {
        this.contractStatus = ContractStatus.SIGNED;
    }

    public void updateSignedPdfUrl(String pdfUrl) {
        this.signedPdfUrl = pdfUrl;
    }

    public void delete() {
        this.delYn = "Y";
    }

    public void reject(String reason) {
        this.contractStatus = ContractStatus.REJECTED;
        this.rejectReason = reason;
    }

    public void cancel(String cancelReason) {
        this.contractStatus = ContractStatus.CANCELED;
        this.cancelReason = cancelReason;
    }
}
