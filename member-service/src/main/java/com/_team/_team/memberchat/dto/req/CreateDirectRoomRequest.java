package com._team._team.memberchat.dto.req;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateDirectRoomRequest(@NotNull UUID otherMemberId) {}
