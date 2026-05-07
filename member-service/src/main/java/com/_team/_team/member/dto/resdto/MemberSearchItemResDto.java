package com._team._team.member.dto.resdto;

import com._team._team.member.domain.enums.EmploymentType;
import com._team._team.member.domain.enums.MemberStatus;
import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

// 사원 검색 결과 한 줄짜리
@Getter
public class MemberSearchItemResDto {

    private final UUID memberId;
    private final String name;
    private final String sabun;
    private final String email;
    private final String profileUrl;
    private final LocalDate joinDate;
    private final MemberStatus memberStatus;
    private final EmploymentType employmentType;

    // 활성 포지션 기반 조직/직책/직급 (없으면 null)
    private final UUID organizationId;
    private final String organizationName;
    private final UUID jobTitleId;
    private final String jobTitleName;
    private final UUID jobGradeId;
    private final String jobGradeName;

    // QueryDSL이 select 결과 그대로 넣어줌
    @QueryProjection
    public MemberSearchItemResDto(UUID memberId,
                                  String name,
                                  String sabun,
                                  String email,
                                  String profileUrl,
                                  LocalDate joinDate,
                                  MemberStatus memberStatus,
                                  EmploymentType employmentType,
                                  UUID organizationId,
                                  String organizationName,
                                  UUID jobTitleId,
                                  String jobTitleName,
                                  UUID jobGradeId,
                                  String jobGradeName) {
        this.memberId = memberId;
        this.name = name;
        this.sabun = sabun;
        this.email = email;
        this.profileUrl = profileUrl;
        this.joinDate = joinDate;
        this.memberStatus = memberStatus;
        this.employmentType = employmentType;
        this.organizationId = organizationId;
        this.organizationName = organizationName;
        this.jobTitleId = jobTitleId;
        this.jobTitleName = jobTitleName;
        this.jobGradeId = jobGradeId;
        this.jobGradeName = jobGradeName;
    }
}
