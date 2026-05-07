package com._team._team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberSavedEvent {
    private UUID memberId;
    private UUID companyId;
    private String name;
    private List<OrganizationSavedEvent> organizationList;
    private List<String> titleName;
    private String phoneNumber;
    private String email;
    private String memberStatus;
    private String position;

    // AI 컨텍스트용 추가 필드
    private String sabun;
    private LocalDate joinDate;
    private String jobGradeName;
    private int esgScore;
}
