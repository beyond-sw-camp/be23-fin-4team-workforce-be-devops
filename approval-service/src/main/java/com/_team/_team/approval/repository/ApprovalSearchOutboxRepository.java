package com._team._team.approval.repository;

import com._team._team.approval.domain.ApprovalSearchOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ApprovalSearchOutboxRepository
        extends JpaRepository<ApprovalSearchOutboxEvent, UUID> {

    List<ApprovalSearchOutboxEvent> findByProcessed(String processed);

    void deleteByProcessedAndCreatedAtBefore(String processed, LocalDateTime before);
}