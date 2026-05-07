package com._team._team.goal.feignclients.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class MemberOrgContextDto {
    private UUID memberId;
    private UUID memberPositionId;
    private UUID organizationId;
    private String organizationName;
    private String memberStatus;
    private String employmentType;
    private LocalDate joinDate;
}
