package com._team._team.member.dto.resdto;

import com._team._team.member.domain.MemberPosition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
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

    public static MemberPositionResDto fromEntity(MemberPosition mp) {
        return MemberPositionResDto.builder()
                .memberPositionId(mp.getMemberPositionId())
                .memberId(mp.getMember().getMemberId())
                .memberName(mp.getMember().getName())
                .organizationId(mp.getOrganization().getOrganizationId())
                .organizationName(mp.getOrganization().getName())
                .jobTitleId(mp.getJobTitle().getJobTitleId())
                .jobTitleName(mp.getJobTitle().getName())
                .jobGradeId(mp.getJobGrade().getJobGradeId())
                .jobGradeName(mp.getJobGrade().getName())
                .build();
    }
}
