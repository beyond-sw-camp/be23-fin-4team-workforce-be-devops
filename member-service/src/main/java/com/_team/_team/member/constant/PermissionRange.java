package com._team._team.member.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PermissionRange {
    SELF("PR001", "본인"),
    TEAM("PR002", "직속 팀"),
    DEPARTMENT("PR003", "부서 전체"),
    COMPANY("PR004", "전사");

    private final String codeValue;
    private final String codeName;

}
