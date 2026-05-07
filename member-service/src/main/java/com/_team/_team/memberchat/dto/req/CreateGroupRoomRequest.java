package com._team._team.memberchat.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateGroupRoomRequest(
        @NotBlank @Size(max = 200) String name,
        @NotEmpty List<UUID> memberIds
) {}
