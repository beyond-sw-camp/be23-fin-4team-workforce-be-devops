package com._team._team.organization.dto.resdto;

import com._team._team.member.domain.Member;
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
public class OrgChartMemberResDto {

    private UUID memberId;
    private String name;
    private String jobGradeName;
    /** 직책명 (인사발령 직책 변경에 사용, 없으면 null) */
    private String jobTitleName;
    /** 프로필 이미지 URL(없으면 null) */
    private String profileUrl;

    public static OrgChartMemberResDto fromEntity(Member member, MemberPosition position) {
        String url = member.getProfileUrl();
        if (url != null) {
            url = url.trim();
            if (url.isEmpty()) {
                url = null;
            }
        }
        return OrgChartMemberResDto.builder()
                .memberId(member.getMemberId())
                .name(member.getName())
                .jobGradeName(position.getJobGrade().getName())
                .jobTitleName(position.getJobTitle().getName())
                .profileUrl(url)
                .build();
    }
}
