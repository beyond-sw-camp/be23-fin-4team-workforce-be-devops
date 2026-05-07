package com._team._team.evaluation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * EvaluationFeedback (v1)
 *
 *  결과 공개 후 본인이 남기는 read-only 코멘트.
 *  시즌 admin 만 조회 가능. 정식 재심 워크플로우는 v2.
 */
@Entity
@Table(name = "evaluation_feedback")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EvaluationFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "feedback_id")
    private UUID feedbackId;

    @Column(name = "response_id", nullable = false)
    private UUID responseId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
