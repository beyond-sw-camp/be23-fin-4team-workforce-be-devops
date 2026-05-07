package com._team._team.search.domain;

import com._team._team.event.OrganizationSavedEvent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.annotation.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.*;

import org.springframework.data.elasticsearch.core.suggest.Completion;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(indexName = "member", createIndex = true)
@Setting(settingPath = "elastic/member-setting.json")
@Mapping(mappingPath = "elastic/member-mapping.json")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemberDocument {

    @Id
    @Field(type = FieldType.Keyword)
    private String memberId;

    @Field(type = FieldType.Keyword, name = "companyId")
    private String companyId;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Nested)
    private List<OrganizationSavedEvent> organizationList;

    @Field(type = FieldType.Text)
    private List<String> titleName;

    @Field(type = FieldType.Keyword)
    private String phoneNumber;

    @Field(type = FieldType.Text)
    private String email;

    @Field(type = FieldType.Text)
    private String memberStatus;

    @Field(type = FieldType.Text)
    private String position;

    @CompletionField(maxInputLength = 100)
    private Completion suggest;
}