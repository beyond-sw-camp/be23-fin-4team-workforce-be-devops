package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationSavedEvent {


    private UUID organizationId;
    private String name;
    private UUID parentId;
    private UUID companyId;
    private Integer displayOrder;
}