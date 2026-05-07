package com._team._team.evaluation.feignclients.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * member-service의 /member/internal/profiles 응답 단위.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberMinimalProfileDto {
    private String name;
    private String department;
    private String positionName;
    private String profileUrl;
}
