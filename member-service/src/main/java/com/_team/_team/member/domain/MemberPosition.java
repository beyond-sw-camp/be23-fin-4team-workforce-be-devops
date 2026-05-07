package com._team._team.member.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.organization.domain.JobGrade;
import com._team._team.organization.domain.JobTitle;
import com._team._team.organization.domain.Organization;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MemberPosition extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID memberPositionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
            nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
            nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_title_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
            nullable = false)
    private JobTitle jobTitle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_grade_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
            nullable = false)
    private JobGrade jobGrade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
            nullable = false)
    private Role role;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    @Column(nullable = false)
    @Builder.Default
    private String isActiveYn = "YES";

    @Column(nullable = false)
    @Builder.Default
    private String isSystemAdminYn = "NO";

    @Column(nullable = false)
    @Builder.Default
    private String delYn = "NO";

    // 비즈니스 메서드
    public void grantSystemAdmin() {
        this.isSystemAdminYn = "YES";
    }

    public void revokeSystemAdmin() {
        this.isSystemAdminYn = "NO";
    }

    public void deactivate() {
        this.isActiveYn = "NO";
        this.endDate = LocalDate.now();
    }

    public void activate() {
        this.isActiveYn = "YES";
        this.endDate = null;
    }

    public void delete() {
        this.delYn = "YES";
    }

    public void updateRole(Role role) {
        this.role = role;
    }
    public void update(Organization organization, JobGrade jobGrade,
                       JobTitle jobTitle, Role role) {
        this.organization = organization;
        this.jobGrade = jobGrade;
        this.jobTitle = jobTitle;
        this.role = role;
    }


}

