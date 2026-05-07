package com._team._team.evaluation.util;

import com._team._team.goal.domain.enums.Grade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 등급 → 점수 산출기 (v2 단순화 — 목표 단위 등급).
 *
 *  점수 매핑:
 *    디폴트 : S=100, A=85, B=70, C=55
 *
 *  최종 점수 산출:
 *    goalScore  = scale[goal grade]                   // 0~100
 *    finalScore = Σ goalScore × goal.weightPct / 100  // 목표 가중치 합산
 */
public final class GradeScoreCalculator {

    public static final Map<Grade, Integer> DEFAULT_SCALE;

    static {
        Map<Grade, Integer> m = new HashMap<>();
        m.put(Grade.S, 100);
        m.put(Grade.A, 85);
        m.put(Grade.B, 70);
        m.put(Grade.C, 55);
        DEFAULT_SCALE = Map.copyOf(m);
    }

    private GradeScoreCalculator() {}

    public static int score(Grade grade, Map<Grade, Integer> scale) {
        if (grade == null) return 0;
        Map<Grade, Integer> use = (scale == null || scale.isEmpty()) ? DEFAULT_SCALE : scale;
        Integer s = use.get(grade);
        return s == null ? 0 : s;
    }

    public static BigDecimal goalScore(Grade grade, Map<Grade, Integer> scale) {
        return BigDecimal.valueOf(score(grade, scale)).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal finalScore(GoalSnapshot snapshot,
                                        Map<UUID, Grade> goalGrades,
                                        Map<Grade, Integer> scale) {
        if (snapshot == null || snapshot.getGoals() == null || snapshot.getGoals().isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (GoalSnapshot.GoalEntry goal : snapshot.getGoals()) {
            Grade g = goalGrades.get(goal.getGoalId());
            BigDecimal gScore = goalScore(g, scale);
            sum = sum.add(gScore
                    .multiply(BigDecimal.valueOf(goal.getWeightPct()))
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }
}
