package com._team._team.search.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(indexName = "approval", createIndex = true)
@Setting(settingPath = "elastic/approval-setting.json")
@Mapping(mappingPath = "elastic/approval-mapping.json")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApprovalDocument {

    @Id
    @Field(type = FieldType.Keyword)
    private String requestId;

    @Field(type = FieldType.Keyword)
    private String companyId;

    @Field(type = FieldType.Keyword)
    private String memberId;

    @Field(type = FieldType.Text)
    private String requesterName;

    @Field(type = FieldType.Text)
    private String requesterOrganizationName;

    @Field(type = FieldType.Keyword)
    private String requesterOrganizationId;

    @Field(type = FieldType.Text)
    private String documentName;

    @Field(type = FieldType.Keyword)
    private String requestStatus;

    @Field(type = FieldType.Keyword)
    private String requestType;

    @Field(type = FieldType.Text)
    private String contentJson;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Keyword)
    private String isDeptVisibleYn;
}