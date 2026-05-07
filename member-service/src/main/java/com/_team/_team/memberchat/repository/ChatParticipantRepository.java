package com._team._team.memberchat.repository;

import com._team._team.memberchat.domain.ChatParticipant;
import com._team._team.memberchat.domain.enums.ChatParticipantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    Optional<ChatParticipant> findByChatRoomIdAndMemberId(Long chatRoomId, UUID memberId);

    long countByChatRoom_IdAndStatus(Long chatRoomId, ChatParticipantStatus status);

    List<ChatParticipant> findByChatRoomId(Long chatRoomId);

    List<ChatParticipant> findByMemberIdAndStatus(UUID memberId, ChatParticipantStatus status);

    /**
     * 1:1 방 전용 - 상대방의 lastReadMessageId 를 조회한다.
     * 참여자가 3명 이상인 그룹방에서는 의미 없다(호출 측에서 1:1 일 때만 사용).
     * HIDDEN/LEFT/BANNED 는 제외하고 JOINED 인 상대만 대상으로 한다.
     */
    @Query("""
            select p.lastReadMessageId from ChatParticipant p
            where p.chatRoom.id = :roomId
              and p.memberId <> :me
              and p.status = :status
            """)
    List<Long> findOtherPartyLastRead(@Param("roomId") Long roomId,
                                      @Param("me") UUID me,
                                      @Param("status") ChatParticipantStatus status);

    /** 호출자 편의 - JOINED 고정 */
    default List<Long> findOtherPartyLastRead(Long roomId, UUID me) {
        return findOtherPartyLastRead(roomId, me, ChatParticipantStatus.JOINED);
    }

    /**
     * 그룹 방의 "이 메시지를 읽은 참여자 수" 계산용.
     *  - 보낸 사람 본인은 제외(자기 메시지는 자동으로 읽은 것으로 간주하되 카운트에서 제외)
     *  - JOINED 참여자만 대상
     */
    @Query("""
            select count(p) from ChatParticipant p
            where p.chatRoom.id = :roomId
              and p.memberId <> :senderId
              and p.status = :status
              and p.lastReadMessageId is not null
              and p.lastReadMessageId >= :messageId
            """)
    long countReadersAtOrAfter(@Param("roomId") Long roomId,
                               @Param("messageId") Long messageId,
                               @Param("senderId") UUID senderId,
                               @Param("status") ChatParticipantStatus status);

    /** 호출자 편의 - JOINED 고정 */
    default long countReadersAtOrAfter(Long roomId, Long messageId, UUID senderId) {
        return countReadersAtOrAfter(roomId, messageId, senderId, ChatParticipantStatus.JOINED);
    }
}
