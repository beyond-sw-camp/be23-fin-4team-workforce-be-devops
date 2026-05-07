package com._team._team.approval.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "approval_search_outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ApprovalSearchOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Lob
    @Column(name = "payload", nullable = false,
            columnDefinition = "LONGTEXT")
    private String payload;

    @Column(name = "processed", nullable = false)
    private String processed;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public void setProcessed() {
        this.processed = "YES";
    }
}