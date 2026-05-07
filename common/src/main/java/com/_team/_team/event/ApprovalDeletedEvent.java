package com._team._team.event;

import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalDeletedEvent {
    private UUID requestId;
}