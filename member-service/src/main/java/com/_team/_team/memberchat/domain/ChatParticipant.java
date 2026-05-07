package com._team._team.memberchat.domain;

import com._team._team.domain.BaseTimeEntity;
import com._team._team.memberchat.domain.enums.ChatParticipantStatus;
import com._team._team.memberchat.domain.enums.ChatRoomRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mc_chat_participant", uniqueConstraints = {
        @UniqueConstraint(name = "uq_mc_participant", columnNames = {"chat_room_id", "member_id"})
}, indexes = {
        @Index(name = "idx_mc_participant_member", columnList = "member_id,status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChatParticipant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ChatRoomRole role = ChatRoomRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ChatParticipantStatus status = ChatParticipantStatus.JOINED;

    @Column(name = "start_message_id")
    private Long startMessageId;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    private LocalDateTime mutedUntil;

    public boolean isJoined() { return status == ChatParticipantStatus.JOINED; }

    public boolean canSendNotice() {
        return role == ChatRoomRole.OWNER || role == ChatRoomRole.MODERATOR;
    }

    public void updateLastRead(Long messageId) {
        if (messageId == null) return;
        if (this.lastReadMessageId == null || this.lastReadMessageId < messageId) {
            this.lastReadMessageId = messageId;
        }
    }

    public void leave() {
        this.status = ChatParticipantStatus.LEFT;
    }

    public void hide() {
        this.status = ChatParticipantStatus.HIDDEN;
    }

    /**
     * HIDDEN 상태였던 참여자를 다시 JOINED 로 복구한다.
     * 재진입 시 이전 메시지를 숨기려면 {@code resumeFromMessageId} 로 start cursor 를 이동시킬 수 있다.
     */
    public void restoreFromHidden(Long resumeFromMessageId) {
        if (this.status == ChatParticipantStatus.HIDDEN) {
            this.status = ChatParticipantStatus.JOINED;
        }
        if (resumeFromMessageId != null) {
            this.startMessageId = resumeFromMessageId;
        }
    }

    public void changeRole(ChatRoomRole newRole) {
        if (newRole != null) {
            this.role = newRole;
        }
    }

    public void muteUntil(LocalDateTime until) {
        this.mutedUntil = until;
    }

    public boolean isMuted() {
        return mutedUntil != null && mutedUntil.isAfter(LocalDateTime.now());
    }
}
