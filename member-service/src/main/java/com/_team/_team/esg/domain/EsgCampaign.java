package com._team._team.esg.domain;

import com._team._team.company.domain.Company;
import com._team._team.domain.BaseTimeEntity;
import com._team._team.esg.domain.enums.CampaignStatus;
import com._team._team.esg.domain.enums.EsgCategory;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EsgCampaign extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID esgCampaignId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
            nullable = false)
    private Company company;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EsgCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CampaignStatus status = CampaignStatus.ACTIVE;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private int rewardPoints;

    // null = 인원 제한 없음
    private Integer maxParticipants;

    // 생성한 관리자 memberId
    @Column(nullable = false)
    private UUID createdBy;

    public void close() { this.status = CampaignStatus.CLOSED; }

    public void update(String title, String description,
                       LocalDate endDate, int rewardPoints, Integer maxParticipants) {
        this.title           = title;
        this.description     = description;
        this.endDate         = endDate;
        this.rewardPoints    = rewardPoints;
        this.maxParticipants = maxParticipants;
    }

    public boolean isJoinable() {
        return status == CampaignStatus.ACTIVE && !LocalDate.now().isAfter(endDate);
    }
}