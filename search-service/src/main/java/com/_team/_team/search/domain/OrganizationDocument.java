package com._team._team.search.domain;


import com._team._team.event.MemberSavedEvent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(indexName = "organization", createIndex = true)
@Setting(settingPath = "elastic/organization-setting.json")
@Mapping(mappingPath = "elastic/organization-mapping.json")
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrganizationDocument {

    @Id
    private String organizationId;

    @Field(type = FieldType.Keyword, name = "companyId")
    private String companyId;

    @Field(type = FieldType.Text, analyzer = "korean_analyzer")
    private String label;

    @Field(type = FieldType.Keyword)
    private String parentId;

    @Field(type = FieldType.Nested)
    private List<Member> memberList;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Member {

        private UUID id;
        private String name;
        private String email;
        private String phoneNumber;
        private List<String> titleName;
        private String memberStatus;
        private String position;

        public void updateMember(MemberSavedEvent event) {
            this.id = event.getMemberId();
            this.name = event.getName();
            this.email = event.getEmail();
            this.phoneNumber = event.getPhoneNumber();
            this.titleName = event.getTitleName();
            this.memberStatus = event.getMemberStatus();
            this.position = event.getPosition();
        }
    }
}
