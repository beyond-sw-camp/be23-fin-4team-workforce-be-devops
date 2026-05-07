package com._team._team.goal.domain.enums;

/**
 * 달성항목 등급 (S~C 4단계).
 * 시즌별 점수 매핑은 EvaluationDesign.gradeScale 에서 override 가능.
 * 시스템 디폴트 점수: S=100 / A=85 / B=70 / C=55
 */
public enum Grade {
    S(100),
    A(85),
    B(70),
    C(55);

    private final int defaultScore;

    Grade(int defaultScore) {
        this.defaultScore = defaultScore;
    }

    public int getDefaultScore() {
        return defaultScore;
    }
}
