package com._team._team.member.dto.resdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DashboardProfileResDto {

    private UUID memberId;
    private String name;
    private String profileUrl;
    private String organizationName;
    private String jobGradeName;
    private String jobTitleName;
    private int todayEventCount;
}
