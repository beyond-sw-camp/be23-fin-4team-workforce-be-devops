package com._team._team.member.dto.resdto;

import com._team._team.member.domain.MemberPosition;
import com._team._team.member.domain.Member;
import com._team._team.member.domain.enums.EmploymentType;
import com._team._team.member.domain.enums.MemberStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberOrgContextResDto {

    private UUID memberId;
    private UUID memberPositionId;
    private UUID organizationId;
    private String organizationName;
    private MemberStatus memberStatus;
    private EmploymentType employmentType;
    private LocalDate joinDate;

    public static MemberOrgContextResDto fromEntity(Member member, MemberPosition position) {
        return MemberOrgContextResDto.builder()
                .memberId(member.getMemberId())
                .memberPositionId(position.getMemberPositionId())
                .organizationId(position.getOrganization().getOrganizationId())
                .organizationName(position.getOrganization().getName())
                .memberStatus(member.getMemberStatus())
                .employmentType(member.getEmploymentType())
                .joinDate(member.getJoinDate())
                .build();
    }
}
