package com._team._team.memberchat;

import com._team._team.memberchat.domain.ChatParticipant;
import com._team._team.memberchat.domain.enums.ChatParticipantStatus;
import com._team._team.memberchat.domain.enums.ChatRoomRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatParticipantTest {

    @Test
    void updateLastRead_isMonotonic() {
        ChatParticipant p = ChatParticipant.builder()
                .role(ChatRoomRole.MEMBER)
                .status(ChatParticipantStatus.JOINED)
                .build();

        p.updateLastRead(10L);
        assertThat(p.getLastReadMessageId()).isEqualTo(10L);

        // older ack must not regress
        p.updateLastRead(5L);
        assertThat(p.getLastReadMessageId()).isEqualTo(10L);

        // newer ack advances
        p.updateLastRead(42L);
        assertThat(p.getLastReadMessageId()).isEqualTo(42L);

        // null is ignored
        p.updateLastRead(null);
        assertThat(p.getLastReadMessageId()).isEqualTo(42L);
    }

    @Test
    void canSendNotice_onlyOwnerOrModerator() {
        ChatParticipant owner = ChatParticipant.builder().role(ChatRoomRole.OWNER).build();
        ChatParticipant mod = ChatParticipant.builder().role(ChatRoomRole.MODERATOR).build();
        ChatParticipant member = ChatParticipant.builder().role(ChatRoomRole.MEMBER).build();

        assertThat(owner.canSendNotice()).isTrue();
        assertThat(mod.canSendNotice()).isTrue();
        assertThat(member.canSendNotice()).isFalse();
    }
}
