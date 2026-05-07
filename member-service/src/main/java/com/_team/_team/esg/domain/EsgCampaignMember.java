package com._team._team.esg.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.member.domain.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "esg_campaign_member",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_campaign_member",
                columnNames = {"esg_campaign_id", "member_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EsgCampaignMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID esgCampaignMemberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "esg_campaign_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
            nullable = false)
    private EsgCampaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
            nullable = false)
    private Member member;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime joinedAt = LocalDateTime.now();

    // null = 미완료
    private LocalDateTime completedAt;

    private Integer pointsEarned;

    public void complete(int points) {
        this.completedAt  = LocalDateTime.now();
        this.pointsEarned = points;
    }

    public boolean isCompleted() { return completedAt != null; }
    public boolean isOwner(UUID memberId) { return this.member.getMemberId().equals(memberId); }
}
