package com._team._team.esg.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.esg.domain.enums.PointType;
import com._team._team.esg.domain.enums.ReferenceType;
import com._team._team.member.domain.Member;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "esg_point_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EsgPointHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID esgPointHistoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
            nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointType pointType;

    // 느슨한 참조 — FK 없음, 이력은 불변 로그
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReferenceType referenceType;

    @Column(nullable = false)
    private UUID referenceId;

    // 항상 양수, 방향은 pointType 으로 구분
    @Column(nullable = false)
    private int points;

    // 트랜잭션 후 잔액 스냅샷
    @Column(nullable = false)
    private int balance;

    private String description;

    // 정적 팩토리
    public static EsgPointHistory earn(Member member,
                                       ReferenceType referenceType, UUID referenceId,
                                       int points, int balance, String description) {
        return EsgPointHistory.builder()
                .member(member)
                .pointType(PointType.EARN)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .points(points)
                .balance(balance)
                .description(description)
                .build();
    }

    public static EsgPointHistory use(Member member,
                                      ReferenceType referenceType, UUID referenceId,
                                      int points, int balance, String description) {
        return EsgPointHistory.builder()
                .member(member)
                .pointType(PointType.USE)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .points(points)
                .balance(balance)
                .description(description)
                .build();
    }
}