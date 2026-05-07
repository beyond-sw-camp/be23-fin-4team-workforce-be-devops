package com._team._team.calendar.domain;

import com._team._team.company.domain.Company;
import com._team._team.domain.BaseTimeEntity;
import com._team._team.member.domain.Member;
import com._team._team.calendar.domain.enums.EventType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CalendarEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID calendarEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
            nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
            nullable = false)
    private Member member;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;  // PERSONAL, TEAM

    // 팀원들에게 공개 여부
    @Column(nullable = false)
    @Builder.Default
    private String isPublicYn = "YES";

    @Column(nullable = false)
    @Builder.Default
    private String delYn = "NO";

    @Column(unique = true)
    private UUID referenceId; // 결재 requestId (APPROVAL 타입 중복 방지)

    public void update(String title, String description,
                       LocalDateTime startAt, LocalDateTime endAt,
                       String isPublicYn) {
        this.title      = title;
        this.description = description;
        this.startAt    = startAt;
        this.endAt      = endAt;
        this.isPublicYn = isPublicYn;
    }

    public void delete() { this.delYn = "YES"; }
}
