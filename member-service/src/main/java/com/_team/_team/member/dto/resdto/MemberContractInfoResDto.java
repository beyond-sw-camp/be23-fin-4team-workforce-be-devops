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
public class MemberContractInfoResDto {
    private UUID memberId;
    private String name;
    private String sabun;
    private String organizationName;
    private String jobTitleName;
    private String jobGradeName;
}
