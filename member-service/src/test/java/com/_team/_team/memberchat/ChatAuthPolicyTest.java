package com._team._team.memberchat;

import com._team._team.memberchat.config.ChatStompHandler.AuthPrincipal;
import com._team._team.memberchat.domain.ChatParticipant;
import com._team._team.memberchat.domain.enums.ChatParticipantStatus;
import com._team._team.memberchat.domain.ChatRoom;
import com._team._team.memberchat.domain.enums.ChatRoomRole;
import com._team._team.memberchat.error.ChatException;
import com._team._team.memberchat.repository.ChatParticipantRepository;
import com._team._team.memberchat.repository.ChatRoomRepository;
import com._team._team.memberchat.service.ChatAuthPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

class ChatAuthPolicyTest {

    @Mock ChatRoomRepository roomRepo;
    @Mock ChatParticipantRepository participantRepo;
    @InjectMocks ChatAuthPolicy policy;

    private ChatRoom room;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        room = ChatRoom.builder().companyId(UUID.randomUUID()).build();
        when(roomRepo.findById(anyLong())).thenReturn(Optional.of(room));
    }

    @Test
    void hrAdmin_canSubscribe_withoutParticipantRecord() {
        AuthPrincipal admin = new AuthPrincipal(UUID.randomUUID(), UUID.randomUUID(), "HR_ADMIN");
        // No participant record exists, but admin should bypass the check.
        policy.canSubscribe(admin, 1L);
        // reaching here means no exception
        assertThat(true).isTrue();
    }

    @Test
    void employee_withoutParticipantRecord_cannotSubscribe() {
        AuthPrincipal emp = new AuthPrincipal(UUID.randomUUID(), UUID.randomUUID(), "EMPLOYEE");
        when(participantRepo.findByChatRoomIdAndMemberId(anyLong(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policy.canSubscribe(emp, 1L))
                .isInstanceOf(ChatException.class);
    }

    @Test
    void leftParticipant_cannotSubscribe() {
        AuthPrincipal emp = new AuthPrincipal(UUID.randomUUID(), UUID.randomUUID(), "EMPLOYEE");
        ChatParticipant left = ChatParticipant.builder()
                .role(ChatRoomRole.MEMBER)
                .status(ChatParticipantStatus.LEFT)
                .build();
        when(participantRepo.findByChatRoomIdAndMemberId(anyLong(), any())).thenReturn(Optional.of(left));

        assertThatThrownBy(() -> policy.canSubscribe(emp, 1L))
                .isInstanceOf(ChatException.class);
    }

    @Test
    void joinedParticipant_canSubscribe() {
        AuthPrincipal emp = new AuthPrincipal(UUID.randomUUID(), UUID.randomUUID(), "EMPLOYEE");
        ChatParticipant joined = ChatParticipant.builder()
                .role(ChatRoomRole.MEMBER)
                .status(ChatParticipantStatus.JOINED)
                .build();
        when(participantRepo.findByChatRoomIdAndMemberId(anyLong(), any())).thenReturn(Optional.of(joined));

        policy.canSubscribe(emp, 1L);
        assertThat(true).isTrue();
    }
}
