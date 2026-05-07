package com._team._team.contract.domain;

import com._team._team.contract.domain.enums.PartyRole;
import com._team._team.contract.domain.enums.SignStatus;
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
public class ContractParty extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false)
    private UUID partyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column(nullable = false)
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PartyRole partyRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SignStatus signStatus = SignStatus.PENDING;

    private LocalDateTime signedAt;

    @Column(columnDefinition = "TEXT")
    private String signatureImageUrl;

    public void sign(String signatureImageUrl) {
        this.signStatus = SignStatus.SIGNED;
        this.signedAt = LocalDateTime.now();
        this.signatureImageUrl = signatureImageUrl;
    }

    public void reject() {
        this.signStatus = SignStatus.REJECTED;
    }

    public void cancel() {
        this.signStatus = SignStatus.CANCELED;
    }
}
