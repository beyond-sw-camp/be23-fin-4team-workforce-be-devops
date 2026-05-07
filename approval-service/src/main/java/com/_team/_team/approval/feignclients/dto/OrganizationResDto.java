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
public class OrganizationResDto {
    private UUID organizationId;
    private String name;
    private UUID parentId;
    private UUID companyId;
}
