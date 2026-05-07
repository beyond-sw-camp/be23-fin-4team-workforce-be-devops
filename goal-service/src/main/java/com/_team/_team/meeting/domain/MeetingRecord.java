package com._team._team.meeting.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.meeting.domain.enums.Reaction;
import com._team._team.meeting.domain.enums.RepeatCycle;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "meeting_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MeetingRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "meeting_record_id")
    private UUID meetingRecordId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_record_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private MeetingRecord parentRecord;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_cycle", nullable = false)
    private RepeatCycle repeatCycle;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "agenda", columnDefinition = "TEXT")
    private String agenda;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    @Column(name = "private_memo", columnDefinition = "TEXT")
    private String privateMemo;

    @Enumerated(EnumType.STRING)
    @Column(name = "manager_reaction")
    private Reaction managerReaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_reaction")
    private Reaction memberReaction;

    /**
     * [M9] 회의에서 논의하는 연관 목표 ID 목록 (JSON 배열).
     * 미팅이 단순 일정관리가 아니라 목표/성과 기반 대화로 연결되도록 추적합니다.
     */
    @Column(name = "related_goal_ids_json", columnDefinition = "TEXT")
    private String relatedGoalIdsJson;

    /**
     * [M9] 연결된 평가 응답 ID — 피드백 면담으로 자동 생성된 회의 혹은 수동으로 평가와 연결한 회의.
     */
    @Column(name = "related_evaluation_response_id")
    private UUID relatedEvaluationResponseId;

    /**
     * [M9] 이 미팅이 속한 평가 시즌 (피드백 면담 추적용).
     */
    @Column(name = "related_season_id")
    private UUID relatedSeasonId;

    @Column(name = "company_id")
    private UUID companyId;

    public void complete(String memo, Reaction managerReaction) {
        if (memo == null || memo.isBlank()) {
            // [M1] 완료 시 memo 는 필수 — 회의록 없는 "완료" 는 의미가 없음
            throw new IllegalArgumentException("면담 완료 시 메모(memo)는 필수입니다.");
        }
        this.completedAt = LocalDateTime.now();
        this.memo = memo;
        this.managerReaction = managerReaction;
    }

    public void linkGoals(String relatedGoalIdsJson) {
        this.relatedGoalIdsJson = relatedGoalIdsJson;
    }

    public void linkEvaluation(UUID seasonId, UUID responseId) {
        this.relatedSeasonId = seasonId;
        this.relatedEvaluationResponseId = responseId;
    }

    public void assignCompany(UUID companyId) {
        this.companyId = companyId;
    }

    public void recordMemberReaction(Reaction memberReaction) {
        this.memberReaction = memberReaction;
    }

    public void updateAgenda(String agenda) {
        this.agenda = agenda;
    }

    public void updatePrivateMemo(String privateMemo) {
        this.privateMemo = privateMemo;
    }

    public void updateScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public void updateRepeatCycle(RepeatCycle repeatCycle) {
        this.repeatCycle = repeatCycle;
    }
}
