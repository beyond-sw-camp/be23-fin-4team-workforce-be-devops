package com._team._team.memberchat.dto.res;

/**
 * 채팅 메시지 목록에 붙는 발신자 스냅샷(동일 회사 멤버 기준).
 */
public record ChatSenderView(
        String name,
        String profileUrl,
        String jobTitleName,
        String jobGradeName,
        String organizationName
) {}
