package com._team._team.esg.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReferenceType {

    ACTIVITY("ESG 활동 인증"),
    CAMPAIGN("캠페인 참여"),
    SHOP_ORDER("사내 물품 구매");


    private final String description;
}