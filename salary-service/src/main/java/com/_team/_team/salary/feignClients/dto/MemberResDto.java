package com._team._team.salary.feignClients.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class MemberResDto {
    private UUID memberId;
    private String name;
    private String sabun;
    private String employmentType;
    private LocalDate joinDate;
    private String bank;
    private String bankAccount;
    private String phoneNumber;
    private String email;
    private String organizationName;
    private String jobGradeName;
    private String jobTitleName;
}