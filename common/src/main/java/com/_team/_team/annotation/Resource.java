package com._team._team.annotation;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Resource {
    MEMBER("RE001", "직원 관리"),
    ORGANIZATION("RE002", "조직 관리"),
    SALARY("RE003", "급여 관리"),
    ATTENDANCE("RE004", "근태 관리"),
    APPROVAL("RE005", "전자결재"),
    ROLE("RE006", "역할/권한 관리"),
    GOAL("RE007", "목표 관리"),
    EVALUATION("RE008", "평가 관리"),
    ESG("RE009", "ESG 관리"),
    MEETING("RE010", "면담 관리"),
    CALENDAR("RE010", "캘린더 관리"),
    APPROVAL_AD("RE011", "결재 관리"),
    CONTRACT("RE012", "계약 관리");

    private final String codeValue;
    private final String codeName;
}
