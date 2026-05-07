package com._team._team.evaluation.util;

import com._team._team.dto.BusinessException;
import com._team._team.goal.domain.enums.KpiCycle;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.Set;

/**
 * 평가 시즌이 봉인할 OKR 회차({@code targetCycle} + {@code targetCycleStart})가
 * 목표(Goal) 쪽 정규 주기 시작일 규칙과 일치하는지 검증한다.
 */
public final class SeasonTargetCycleRules {

    private SeasonTargetCycleRules() {}

    public static void validate(KpiCycle cycle, LocalDate start) {
        if (cycle == null || start == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "targetCycle / targetCycleStart 필수입니다.");
        }
        if (start.getDayOfMonth() != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "targetCycleStart는 해당 주기의 시작월 1일이어야 합니다: " + start);
        }
        int month = start.getMonthValue();
        switch (cycle) {
            case QUARTERLY -> {
                Set<Integer> ok = Set.of(1, 4, 7, 10);
                if (!ok.contains(month)) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST,
                            "분기(QUARTERLY) 시즌의 targetCycleStart는 1·4·7·10월 1일만 허용됩니다: " + start);
                }
            }
            case HALF_YEARLY -> {
                if (month != 1 && month != 7) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST,
                            "반기(HALF_YEARLY) 시즌의 targetCycleStart는 1월 또는 7월 1일만 허용됩니다: " + start);
                }
            }
            case YEARLY -> {
                if (month != 1) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST,
                            "연간(YEARLY) 시즌의 targetCycleStart는 1월 1일만 허용됩니다: " + start);
                }
            }
        }
    }
}
