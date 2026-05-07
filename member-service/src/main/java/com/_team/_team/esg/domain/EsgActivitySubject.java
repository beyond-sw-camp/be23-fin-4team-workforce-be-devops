package com._team._team.esg.domain;

import com._team._team.company.domain.Company;
import com._team._team.domain.BaseTimeEntity;
import com._team._team.esg.domain.enums.EsgCategory;
import com._team._team.esg.domain.enums.VerificationType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EsgActivitySubject extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID esgActivitySubjectId;

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


    @Column(nullable = false)
    @Builder.Default
    private int defaultPoints = 0;

    // 시스템 기본 제공 양식 여부 (회사가 만든 건 NO)
    @Column(nullable = false)
    @Builder.Default
    private String isDefaultYn = "NO";

    @Column(nullable = false)
    @Builder.Default
    private String delYn = "NO";

    public void update(String title, String description,
                       EsgCategory category, int defaultPoints) {
        this.title         = title;
        this.description   = description;
        this.category      = category;
        this.defaultPoints = defaultPoints;
    }

    public void delete() { this.delYn = "YES"; }
}