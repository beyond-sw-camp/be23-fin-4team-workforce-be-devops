package com._team._team.search.dto.resdto;

import com._team._team.event.OrganizationSavedEvent;
import com._team._team.search.domain.MemberDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberSearchResDto {

    private String memberId;
    private String name;
    private String email;
    private String phoneNumber;
    private String memberStatus;
    private String position;
    private List<OrganizationSavedEvent> organizationList;
    private List<String> titleName;

    public static MemberSearchResDto fromDocument(MemberDocument doc) {
        return MemberSearchResDto.builder()
                .memberId(doc.getMemberId())
                .name(doc.getName())
                .email(doc.getEmail())
                .phoneNumber(doc.getPhoneNumber())
                .memberStatus(doc.getMemberStatus())
                .position(doc.getPosition())
                .organizationList(doc.getOrganizationList())
                .titleName(doc.getTitleName())
                .build();
    }
}