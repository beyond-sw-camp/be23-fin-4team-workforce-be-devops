package com._team._team.memberchat.dto.res;

import com._team._team.memberchat.domain.ChatParticipant;
import com._team._team.memberchat.domain.enums.ChatParticipantStatus;
import com._team._team.memberchat.domain.enums.ChatRoomRole;

import java.util.UUID;

/**
 * 채팅방 참여자 상세(프로필·직급·소속·역할) 응답.
 * 방 참여자만 조회 가능(서비스 단에서 인가 체크).
 */
public record ChatParticipantResponse(
        UUID memberId,
        String name,
        String profileUrl,
        String jobTitleName,
        String jobGradeName,
        String organizationName,
        ChatRoomRole role,
        ChatParticipantStatus status,
        Long lastReadMessageId
) {
    public static ChatParticipantResponse from(ChatParticipant participant, ChatSenderView view) {
        String name = view == null ? null : view.name();
        String profileUrl = view == null ? null : view.profileUrl();
        String jobTitle = view == null ? null : view.jobTitleName();
        String jobGrade = view == null ? null : view.jobGradeName();
        String org = view == null ? null : view.organizationName();
        return new ChatParticipantResponse(
                participant.getMemberId(),
                name,
                profileUrl,
                jobTitle,
                jobGrade,
                org,
                participant.getRole(),
                participant.getStatus(),
                participant.getLastReadMessageId()
        );
    }
}
