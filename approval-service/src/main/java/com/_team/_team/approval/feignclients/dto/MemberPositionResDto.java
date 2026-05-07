package com._team._team.approval.feignclients.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class MemberPositionResDto {
    private UUID memberPositionId;
    private UUID memberId;
    private String memberName;
    private UUID organizationId;
    private String organizationName;
    private UUID jobTitleId;
    private String jobTitleName;
    private UUID jobGradeId;
    private String jobGradeName;
}
