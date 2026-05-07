package com._team._team.esg.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.esg.domain.enums.EsgGrade;
import com._team._team.member.domain.Member;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EsgScore extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID esgScoreId;

    // 회사 전체 스냅샷일 때 사용
    @Column(nullable = false)
    private UUID companyId;

    // null → 회사 전체 / 값 존재 → 직원 개인
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Member member;

    // "2025-04" 형식
    @Column(name = "score_year_month", nullable = false)
    private String yearMonth;

    @Column(nullable = false)
    private int eScore;

    @Column(nullable = false)
    private int sScore;

    @Column(nullable = false)
    private int gScore;

    @Column(nullable = false)
    private int totalScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EsgGrade grade;

    public static EsgScore snapshot(UUID companyId, Member member,
                                    String yearMonth, int e, int s, int g) {
        int total = (e + s + g) / 3;
        return EsgScore.builder()
                .companyId(companyId)
                .member(member)
                .yearMonth(yearMonth)
                .eScore(e).sScore(s).gScore(g)
                .totalScore(total)
                .grade(EsgGrade.from(total))
                .build();
    }
}