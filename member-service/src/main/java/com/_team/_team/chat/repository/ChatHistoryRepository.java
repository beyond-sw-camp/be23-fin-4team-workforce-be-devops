package com._team._team.chat.repository;

import com._team._team.chat.domain.ChatHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, UUID> {

    List<ChatHistory> findByMemberIdAndCompanyIdOrderByCreatedAtAsc(
            UUID memberId, UUID companyId);

    void deleteByMemberIdAndCompanyId(UUID memberId, UUID companyId);

    @Query("""
            SELECT h FROM ChatHistory h
            WHERE h.memberId = :memberId
              AND h.companyId = :companyId
              AND h.createdAt > :since
            ORDER BY h.createdAt DESC
            """)
    List<ChatHistory> findRecentHistory(
            @Param("memberId") UUID memberId,
            @Param("companyId") UUID companyId,
            @Param("since") LocalDateTime since,
            Pageable pageable
    );
}