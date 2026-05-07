package com._team._team.approval.dto.resdto;

import com._team._team.approval.feignclients.dto.MemberPositionResDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class PolicyLineCandidateResDto {
    private UUID policyLineId;
    private UUID documentId;
    private UUID jobTitleId;
    private Integer stepOrder;
    private UUID organizationId;
    private List<MemberPositionResDto> candidates;
}
