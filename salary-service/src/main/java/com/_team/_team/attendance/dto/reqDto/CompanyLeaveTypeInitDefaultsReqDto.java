package com._team._team.attendance.dto.reqDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

// 기본 휴가 요청 회사 가입 시 또는 휴가 관리 화면의 [기본 휴가 불러오기]
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CompanyLeaveTypeInitDefaultsReqDto {
    private Set<String> codes;
}
