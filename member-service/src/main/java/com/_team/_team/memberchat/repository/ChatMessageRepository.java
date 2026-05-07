package com._team._team.memberchat.repository;

import com._team._team.memberchat.domain.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Optional<ChatMessage> findByClientMessageId(String clientMessageId);

    /** cursor(before) 기반 페이지네이션: messageId < cursor */
    @Query("""
            select m from ChatMessage m
            where m.chatRoom.id = :roomId and (:cursor is null or m.id < :cursor)
            order by m.id desc
            """)
    List<ChatMessage> pageBefore(@Param("roomId") Long roomId,
                                 @Param("cursor") Long cursor,
                                 Pageable pageable);

    /** 재연결 sync: lastSeenMessageId 초과 메시지 */
    @Query("""
            select m from ChatMessage m
            where m.chatRoom.id = :roomId and m.id > :lastSeen
            order by m.id asc
            """)
    List<ChatMessage> fetchAfter(@Param("roomId") Long roomId,
                                 @Param("lastSeen") Long lastSeenMessageId,
                                 Pageable pageable);

    /** 방의 최신(최대 id) 메시지. */
    Optional<ChatMessage> findTopByChatRoom_IdOrderByIdDesc(Long roomId);

    /**
     * unreadCount = 내가 아직 안 읽은(자기 자신이 보낸 건 제외) 메시지 수.
     *  - lastReadMessageId 가 null 이면 0L 로 취급해 전부 카운트
     *  - 삭제된 메시지는 제외
     */
    @Query("""
            select count(m) from ChatMessage m
            where m.chatRoom.id = :roomId
              and m.senderId <> :memberId
              and m.deleted = false
              and m.id > coalesce(:lastReadMessageId, 0)
            """)
    long countUnread(@Param("roomId") Long roomId,
                     @Param("memberId") java.util.UUID memberId,
                     @Param("lastReadMessageId") Long lastReadMessageId);
}
