package com._team._team.event;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalSavedEvent {
    private UUID requestId;
    private UUID companyId;
    private UUID memberId;
    private String requesterName;
    private String requesterOrganizationName;
    private UUID requesterOrganizationId;
    private String documentName;
    private String requestStatus;
    private String requestType;
    private String contentJson;
    private LocalDateTime createdAt;
    private String isDeptVisibleYn;
}