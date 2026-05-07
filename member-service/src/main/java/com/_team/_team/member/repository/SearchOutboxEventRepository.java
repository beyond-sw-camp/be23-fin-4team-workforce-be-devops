package com._team._team.member.repository;

import com._team._team.member.domain.SearchOutboxEvent;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SearchOutboxEventRepository
        extends JpaRepository<SearchOutboxEvent, UUID> {

    // 미처리 이벤트 조회
    List<SearchOutboxEvent> findByProcessed(String processed);

    // 처리 완료 + 특정 시간 이전 데이터 삭제
    @Modifying
    @Query("DELETE FROM SearchOutboxEvent s " +
            "WHERE s.processed = :processed " +
            "AND s.createdAt < :threshold")
    int deleteByProcessedAndCreatedAtBefore(
            @Param("processed") String processed,
            @Param("threshold") LocalDateTime threshold);
}
