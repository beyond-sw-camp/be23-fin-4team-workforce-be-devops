package com._team._team.memberchat.repository;

import com._team._team.memberchat.domain.ChatRoom;
import com._team._team.memberchat.domain.enums.ChatParticipantStatus;
import com._team._team.memberchat.domain.enums.ChatRoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /**
     * 동일한 두 멤버로 구성된 기존 DIRECT 방 조회 (멤버 2명, deleted=false).
     */
    @Query("""
            select p.chatRoom from ChatParticipant p
            where p.chatRoom.type = :type
              and p.chatRoom.companyId = :companyId
              and p.chatRoom.deleted = false
              and p.memberId in (:a, :b)
            group by p.chatRoom
            having count(distinct p.memberId) = 2
            """)
    Optional<ChatRoom> findDirectRoomOf(@Param("companyId") UUID companyId,
                                        @Param("a") UUID a,
                                        @Param("b") UUID b,
                                        @Param("type") ChatRoomType type);

    /**
     * 내가 참여한 활성 방 목록.
     */
    @Query("""
            select distinct r from ChatRoom r
              join ChatParticipant p on p.chatRoom = r
            where p.memberId = :memberId
              and p.status = :status
              and r.deleted = false
            order by r.updatedAt desc
            """)
    List<ChatRoom> findMyRooms(@Param("memberId") UUID memberId,
                               @Param("status") ChatParticipantStatus status);

    /** 호출자 편의 - JOINED 고정 */
    default List<ChatRoom> findMyRooms(UUID memberId) {
        return findMyRooms(memberId, ChatParticipantStatus.JOINED);
    }
}
