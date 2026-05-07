package com._team._team.meeting.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.meeting.domain.enums.TlRating;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "meeting_action")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MeetingAction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "meeting_action_id")
    private UUID meetingActionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_record_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private MeetingRecord meetingRecord;

    /** [M2] assignee 는 누구에게 할 일인지 명확해야 하므로 NOT NULL. */
    @Column(name = "assignee_id", nullable = false)
    private UUID assigneeId;

    @Column(name = "description", length = 500, nullable = false)
    private String description;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "is_completed", nullable = false)
    @Builder.Default
    private boolean isCompleted = false;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "tl_rating")
    private TlRating tlRating;

    @Column(name = "approval_id")
    private UUID approvalId;

    public void complete() {
        this.isCompleted = true;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * [M4] 실제로 수행되지 않은 액션 아이템에 대해 평가를 매기는 것은 의미가 없으므로,
     * 완료된 액션에 대해서만 평가가 가능하도록 가드를 추가.
     */
    public void rate(TlRating tlRating) {
        if (!this.isCompleted) {
            throw new IllegalStateException("완료되지 않은 액션 아이템에는 평가를 할 수 없습니다.");
        }
        this.tlRating = tlRating;
    }

    public void linkApproval(UUID approvalId) {
        this.approvalId = approvalId;
    }
}
