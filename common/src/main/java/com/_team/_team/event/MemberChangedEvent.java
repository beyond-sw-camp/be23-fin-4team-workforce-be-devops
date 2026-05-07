package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class MemberChangedEvent {
    private UUID memberId;
}
