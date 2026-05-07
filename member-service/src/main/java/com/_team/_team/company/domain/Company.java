package com._team._team.company.domain;

import com._team._team.company.domain.enums.CompanyStatus;
import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.apache.kafka.common.protocol.types.Field;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Company extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID companyId;

    @Column(nullable = false)
    private String companyName;

    @Column(nullable = false)
    private String companyDomain;

    @Column(nullable = false)
    private String ceoName;

    @Column(nullable = false, unique = true)
    private String businessNumber;

    private String logoUrl;

    @Column(columnDefinition = "TEXT")
    private String sealImageUrl;

    @Column(nullable = false)
    private String address;

    private String detailAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CompanyStatus status = CompanyStatus.ACTIVE;

    // 비즈니스 메서드
    public void updateLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public void updateCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public void updateAddress(String address, String detailAddress) {
        this.address = address;
        this.detailAddress = detailAddress;
    }

    public void suspend() {
        this.status = CompanyStatus.SUSPENDED;
    }

    public void activate() {
        this.status = CompanyStatus.ACTIVE;
    }

    public void delete() {
        this.status = CompanyStatus.DELETED;
    }

    public void updateSealImageUrl(String sealImageUrl) {
        this.sealImageUrl = sealImageUrl;
    }
}