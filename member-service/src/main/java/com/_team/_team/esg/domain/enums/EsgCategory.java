package com._team._team.esg.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EsgCategory {

    E("환경"),
    S("사회"),
    G("지배구조");

    private final String description;
}
