package com._team._team.calendar.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventType {

    PERSONAL("개인 일정"),
    TEAM("팀 일정"),
    APPROVAL("결재 일정");

    private final String description;
}