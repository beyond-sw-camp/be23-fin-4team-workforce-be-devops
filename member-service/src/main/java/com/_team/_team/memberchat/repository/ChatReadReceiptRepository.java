package com._team._team.memberchat.repository;

import com._team._team.memberchat.domain.ChatReadReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatReadReceiptRepository extends JpaRepository<ChatReadReceipt, Long> {
    Optional<ChatReadReceipt> findByMessageIdAndMemberId(Long messageId, UUID memberId);
}
