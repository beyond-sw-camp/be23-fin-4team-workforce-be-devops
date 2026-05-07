package com._team._team.goal.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.goal.domain.converter.Reaction;
import com._team._team.goal.domain.converter.ReactionListConverter;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "goal_comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GoalComment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "comment_id")
    private UUID commentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Goal goal;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "body", columnDefinition = "TEXT", nullable = false)
    private String body;

    /** 리액션 목록 — DB에는 JSON 배열로 저장 */
    @Convert(converter = ReactionListConverter.class)
    @Column(name = "reactions_json", columnDefinition = "TEXT")
    @Builder.Default
    private List<Reaction> reactions = new ArrayList<>();

    public void updateBody(String body) {
        if (body != null) {
            this.body = body;
        }
    }

    /**
     * 이모지 리액션 토글: 해당 이모지가 없으면 새로 추가, memberId가 이미 있으면 제거.
     * 빈 리액션은 자동 정리.
     */
    public void toggleReaction(String emoji, UUID memberId) {
        Reaction target = reactions.stream()
                .filter(r -> emoji.equals(r.getEmoji()))
                .findFirst()
                .orElse(null);

        if (target == null) {
            target = new Reaction(emoji, new ArrayList<>());
            reactions.add(target);
        }

        target.toggle(memberId);

        // 빈 리액션 제거
        if (target.isEmpty()) {
            reactions.remove(target);
        }
    }
}
