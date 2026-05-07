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
public class MemberContractInfoResDto {
    private UUID memberId;
    private String name;
    private String sabun;
    private String organizationName;
    private String jobTitleName;
}
