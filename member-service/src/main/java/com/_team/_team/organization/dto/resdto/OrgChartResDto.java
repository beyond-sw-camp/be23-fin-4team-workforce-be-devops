package com._team._team.organization.dto.resdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrgChartResDto {

    private UUID organizationId;
    private String name;
    private List<OrgChartMemberResDto> members;  // jobGrades → members 로 변경
    private List<OrgChartResDto> children;
 }
