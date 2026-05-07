package com._team._team.esg.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EsgGrade {

    BRONZE("브론즈",    0),
    SILVER("실버",     40),
    GOLD("골드",       70),
    PLATINUM("플래티넘", 90);

    private final String description;
    private final int    minScore;

    public static EsgGrade from(int score) {
        EsgGrade[] values = values();
        for (int i = values.length - 1; i >= 0; i--) {
            if (score >= values[i].minScore) return values[i];
        }
        return BRONZE;
    }
}