package com._team._team.member.domain;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "search_outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SearchOutboxEvent {

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

    // 처리 완료
    public void setProcessed() {
        this.processed = "YES";
    }
}