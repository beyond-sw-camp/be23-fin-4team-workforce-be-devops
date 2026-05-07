package com._team._team.organization.dto.resdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrgChartWrapperResDto {

    private String companyName;
    private List<OrgChartResDto> organizations;
}