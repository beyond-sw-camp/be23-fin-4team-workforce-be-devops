package com._team._team.evaluation.service.scoring;

import com._team._team.evaluation.domain.converter.DesignQuestion;
import com._team._team.evaluation.domain.converter.EvaluationSection;
import com._team._team.evaluation.domain.enums.SectionType;
import com._team._team.evaluation.dto.resdto.GoalSnapshotDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [L-1] 섹션 타입별 채점기.
 *
 * 설계된 섹션의 {@link SectionType} 에 따라 서로 다른 로직을 적용한다.
 *   - MANUAL        : answersJson 의 숫자형 응답을 기반으로 가중 평균
 *   - KPI_SCORE     : goalSnapshotJson 의 {@code achievementPctAtSnapshot} 기반 가중 평균
 *   - PEER_FEEDBACK : 동료 응답 집계 — Phase C 에서는 스킵 (후속 구현)
 *
 * 반환값은 {@link ScoreBreakdown} 으로 총점과 섹션별 상세를 모두 제공한다.
 * 저장하지 않고, 조회/제출 시점마다 재계산하는 것이 원칙 (design·snapshot 은 시점 보존).
 */
@Component
public class SectionScorer {

    private static final ObjectMapper OM = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** scale 문항 기본 최소/최대. 문항 options 에 min/max 가 없을 때 사용. */
    private static final BigDecimal DEFAULT_SCALE_MIN = BigDecimal.ONE;
    private static final BigDecimal DEFAULT_SCALE_MAX = BigDecimal.valueOf(5);

    /**
     * 응답의 총점 + 섹션별 breakdown 산출 (동료 피드백 집계 없음 — 기존 호출부 호환).
     */
    public ScoreBreakdown score(List<EvaluationSection> sections,
                                String answersJson,
                                List<GoalSnapshotDto> goalSnapshots) {
        return score(sections, answersJson, goalSnapshots, null);
    }

    /**
     * 응답의 총점 + 섹션별 breakdown 산출.
     *
     * @param sections                설계 섹션 목록 (null / empty 허용)
     * @param answersJson             응답 JSON — {@code [{questionId, scaleValue, ...}, ...]} 혹은
     *                                {@code { items: [...] }} 형태
     * @param goalSnapshots           KPI 섹션용 스냅샷 목록 (null 허용)
     * @param peerScoresByQuestion    [Phase D] PEER_FEEDBACK 섹션용 — 질문ID → 동료들의 0~100 점수 리스트.
     *                                null 이면 PEER_FEEDBACK 섹션은 스킵된다.
     */
    public ScoreBreakdown score(List<EvaluationSection> sections,
                                String answersJson,
                                List<GoalSnapshotDto> goalSnapshots,
                                Map<String, List<BigDecimal>> peerScoresByQuestion) {
        List<EvaluationSection> safeSections = sections != null ? sections : Collections.emptyList();
        Map<String, BigDecimal> answerScoreByQuestion = parseAnswerScores(answersJson);
        List<GoalSnapshotDto> safeSnapshots = goalSnapshots != null ? goalSnapshots : Collections.emptyList();

        List<SectionScore> results = new ArrayList<>(safeSections.size());
        for (EvaluationSection section : safeSections) {
            SectionType type = section.resolveType();
            switch (type) {
                case MANUAL -> results.add(scoreManual(section, answerScoreByQuestion));
                case KPI_SCORE -> results.add(scoreKpi(section, safeSnapshots));
                case PEER_FEEDBACK -> results.add(scorePeer(section, peerScoresByQuestion));
            }
        }

        BigDecimal total = weightedAverage(results);
        return ScoreBreakdown.builder()
                .totalScore(total)
                .sections(results)
                .build();
    }

    /**
     * [Phase D] 동료 피드백 집계.
     *   - 섹션의 각 채점 가능한 문항에 대해, 동료 응답들의 점수를 평균
     *   - 문항 단위 평균을 다시 문항 weight 로 가중 평균 → 섹션 점수
     * 피어 신호가 전혀 없으면 skip.
     */
    private SectionScore scorePeer(EvaluationSection section,
                                   Map<String, List<BigDecimal>> peerScoresByQuestion) {
        if (peerScoresByQuestion == null || peerScoresByQuestion.isEmpty()) {
            return skip(section, "동료 응답이 없음");
        }
        List<DesignQuestion> qs = section.getQuestions() != null ? section.getQuestions() : Collections.emptyList();
        if (qs.isEmpty()) {
            return skip(section, "문항이 없는 섹션");
        }

        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightAcc = BigDecimal.ZERO;
        int sampleAcc = 0;

        for (DesignQuestion q : qs) {
            if (!isScored(q)) continue;
            List<BigDecimal> scores = peerScoresByQuestion.get(q.getId());
            if (scores == null || scores.isEmpty()) continue;

            BigDecimal sum = BigDecimal.ZERO;
            for (BigDecimal s : scores) sum = sum.add(s);
            BigDecimal avg = sum.divide(BigDecimal.valueOf(scores.size()), 4, RoundingMode.HALF_UP);

            BigDecimal qWeight = q.getWeight() != null ? q.getWeight() : BigDecimal.ONE;
            weightedSum = weightedSum.add(avg.multiply(qWeight));
            weightAcc = weightAcc.add(qWeight);
            sampleAcc += scores.size();
        }

        if (weightAcc.compareTo(BigDecimal.ZERO) == 0) {
            return skip(section, "집계 가능한 동료 점수가 없음");
        }

        BigDecimal avg = weightedSum.divide(weightAcc, 4, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        return SectionScore.builder()
                .sectionId(section.getSectionId())
                .title(section.getTitle())
                .type(SectionType.PEER_FEEDBACK)
                .weight(section.getWeight())
                .score(clamp(avg))
                .skipped(false)
                .sampleSize(sampleAcc)
                .build();
    }

    /**
     * [Phase D] 주어진 동료 응답들의 answersJson 을 파싱해 질문ID → 점수 리스트로 만든다.
     * 자기 자신 응답은 호출부에서 제외 (responseId 비교).
     */
    public Map<String, List<BigDecimal>> aggregatePeerScores(List<String> peerAnswersJsonList) {
        Map<String, List<BigDecimal>> out = new HashMap<>();
        if (peerAnswersJsonList == null || peerAnswersJsonList.isEmpty()) return out;
        for (String json : peerAnswersJsonList) {
            Map<String, BigDecimal> perPeer = parseAnswerScores(json);
            for (var entry : perPeer.entrySet()) {
                out.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
            }
        }
        return out;
    }

    // ─────────────────────────── 섹션 타입별 ───────────────────────────

    private SectionScore scoreManual(EvaluationSection section, Map<String, BigDecimal> answerScoreByQuestion) {
        List<DesignQuestion> qs = section.getQuestions() != null ? section.getQuestions() : Collections.emptyList();
        if (qs.isEmpty()) {
            return skip(section, "문항이 없는 섹션");
        }

        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightAcc = BigDecimal.ZERO;
        int used = 0;

        for (DesignQuestion q : qs) {
            if (!isScored(q)) continue; // text 같은 비점수형은 제외
            BigDecimal s = answerScoreByQuestion.get(q.getId());
            if (s == null) continue;

            BigDecimal qWeight = q.getWeight() != null ? q.getWeight() : BigDecimal.ONE;
            weightedSum = weightedSum.add(s.multiply(qWeight));
            weightAcc = weightAcc.add(qWeight);
            used++;
        }

        if (weightAcc.compareTo(BigDecimal.ZERO) == 0) {
            return skip(section, "채점 가능한 응답이 없음");
        }

        BigDecimal avg = weightedSum.divide(weightAcc, 4, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        return SectionScore.builder()
                .sectionId(section.getSectionId())
                .title(section.getTitle())
                .type(SectionType.MANUAL)
                .weight(section.getWeight())
                .score(clamp(avg))
                .skipped(false)
                .sampleSize(used)
                .build();
    }

    private SectionScore scoreKpi(EvaluationSection section, List<GoalSnapshotDto> snapshots) {
        List<GoalSnapshotDto> filtered = applyKpiFilter(section.getKpiFilter(), snapshots);
        if (filtered.isEmpty()) {
            return skip(section, "대상 KPI 스냅샷이 없음");
        }

        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightAcc = BigDecimal.ZERO;
        int used = 0;

        // [워크플로우 보강] 개별 목표별 기여도 드릴다운 수집 — 결과 화면의 KPI 섹션에서
        // "어떤 목표가 얼마만큼 반영됐는지" 시각화하기 위함. 스키마 변경 없이 조회 시 재계산.
        java.util.List<java.util.Map<String, Object>> usedRaw = new java.util.ArrayList<>();

        for (GoalSnapshotDto snap : filtered) {
            BigDecimal achievement = snap.getRolledAchievementPctAtSnapshot() != null
                    ? snap.getRolledAchievementPctAtSnapshot()
                    : snap.getAchievementPctAtSnapshot();
            if (achievement == null) continue;

            BigDecimal w = snap.getWeightPct() != null && snap.getWeightPct().compareTo(BigDecimal.ZERO) > 0
                    ? snap.getWeightPct()
                    : BigDecimal.ONE;
            weightedSum = weightedSum.add(achievement.multiply(w));
            weightAcc = weightAcc.add(w);
            used++;

            java.util.Map<String, Object> row = new java.util.HashMap<>();
            row.put("snap", snap);
            row.put("achievement", achievement);
            row.put("weight", w);
            usedRaw.add(row);
        }

        if (weightAcc.compareTo(BigDecimal.ZERO) == 0) {
            return skip(section, "집계 가능한 달성률이 없음");
        }

        BigDecimal avg = weightedSum.divide(weightAcc, 4, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);

        // 각 목표의 섹션 점수 기여 비율(100%) — weight 기반.
        List<SectionScore.KpiContribution> contributions = new ArrayList<>(usedRaw.size());
        BigDecimal hundred = BigDecimal.valueOf(100);
        for (var row : usedRaw) {
            GoalSnapshotDto snap = (GoalSnapshotDto) row.get("snap");
            BigDecimal w = (BigDecimal) row.get("weight");
            BigDecimal pct = w.multiply(hundred)
                    .divide(weightAcc, 2, RoundingMode.HALF_UP);
            contributions.add(SectionScore.KpiContribution.builder()
                    .goalId(snap.getGoalId())
                    .title(snap.getTitle())
                    .achievement(clamp((BigDecimal) row.get("achievement")))
                    .weight(snap.getWeightPct())
                    .contributionPct(pct)
                    .build());
        }

        return SectionScore.builder()
                .sectionId(section.getSectionId())
                .title(section.getTitle())
                .type(SectionType.KPI_SCORE)
                .weight(section.getWeight())
                .score(clamp(avg))
                .skipped(false)
                .sampleSize(used)
                .kpiContributions(contributions)
                .build();
    }

    // ─────────────────────────── 헬퍼 ───────────────────────────

    /**
     * 최종 모델에서는 템플릿 기반 KPI 분기를 사용하지 않는다.
     * kpiFilter 값은 입력되어도 무시하고 전체 스냅샷을 사용한다.
     */
    private List<GoalSnapshotDto> applyKpiFilter(String filter, List<GoalSnapshotDto> all) {
        return all;
    }

    /**
     * 총점: 채점 성공한(skipped=false) 섹션들의 weight 가중 평균.
     * 없으면 null.
     */
    private BigDecimal weightedAverage(List<SectionScore> results) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightAcc = BigDecimal.ZERO;
        for (SectionScore r : results) {
            if (r.isSkipped() || r.getScore() == null) continue;
            BigDecimal w = r.getWeight() != null ? r.getWeight() : BigDecimal.ONE;
            weightedSum = weightedSum.add(r.getScore().multiply(w));
            weightAcc = weightAcc.add(w);
        }
        if (weightAcc.compareTo(BigDecimal.ZERO) == 0) return null;
        return weightedSum.divide(weightAcc, 4, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private SectionScore skip(EvaluationSection section, String reason) {
        return SectionScore.builder()
                .sectionId(section.getSectionId())
                .title(section.getTitle())
                .type(section.resolveType())
                .weight(section.getWeight())
                .skipped(true)
                .reason(reason)
                .build();
    }

    private boolean isScored(DesignQuestion q) {
        // text 형은 점수 없음. scale/grade/gap 은 점수 대상.
        return q.getType() != null && !"text".equalsIgnoreCase(q.getType());
    }

    private BigDecimal clamp(BigDecimal v) {
        if (v == null) return null;
        return v.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
    }

    /**
     * answersJson 에서 질문별 0~100 점수를 추출한다.
     *   - scaleValue   : min~max 범위를 0~100 으로 선형 매핑 (기본 1~5)
     *   - gradeValue   : 숫자 파싱 가능하면 그대로 사용 (100점 만점 가정)
     *   - textValue    : 무시
     *
     * 포맷 두 가지 모두 지원:
     *   [{questionId, scaleValue, ...}, ...]
     *   { "items": [{...}] } (레거시 호환)
     */
    private Map<String, BigDecimal> parseAnswerScores(String answersJson) {
        Map<String, BigDecimal> out = new HashMap<>();
        if (answersJson == null || answersJson.isBlank()) return out;
        try {
            JsonNode root = OM.readTree(answersJson);
            JsonNode arr = root.isArray() ? root : root.path("items");
            if (!arr.isArray()) return out;

            for (JsonNode n : arr) {
                String qid = n.path("questionId").asText(null);
                if (qid == null || qid.isBlank()) continue;

                if (n.has("scaleValue") && !n.get("scaleValue").isNull()) {
                    BigDecimal v = BigDecimal.valueOf(n.get("scaleValue").asDouble());
                    BigDecimal normalized = scaleTo100(v, DEFAULT_SCALE_MIN, DEFAULT_SCALE_MAX);
                    out.put(qid, normalized);
                } else if (n.has("gradeValue") && !n.get("gradeValue").isNull()) {
                    String g = n.get("gradeValue").asText();
                    try {
                        BigDecimal v = new BigDecimal(g);
                        out.put(qid, clamp(v));
                    } catch (NumberFormatException ignore) {
                        // 문자 등급 ("A", "B") 은 design.gradeConfig 없이는 점수화 불가 → 스킵
                    }
                } else if (n.has("score") && !n.get("score").isNull()) {
                    // 레거시: 이미 0~100 으로 계산된 score 필드
                    out.put(qid, clamp(BigDecimal.valueOf(n.get("score").asDouble())));
                }
            }
        } catch (Exception ignore) {
            return out;
        }
        return out;
    }

    private BigDecimal scaleTo100(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null || min == null || max == null) return null;
        BigDecimal range = max.subtract(min);
        if (range.compareTo(BigDecimal.ZERO) <= 0) return null;
        BigDecimal pct = value.subtract(min)
                .divide(range, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        return clamp(pct.setScale(2, RoundingMode.HALF_UP));
    }

    /** goalSnapshotJson 문자열을 GoalSnapshotDto 리스트로 역직렬화. */
    public List<GoalSnapshotDto> parseGoalSnapshots(String goalSnapshotJson) {
        if (goalSnapshotJson == null || goalSnapshotJson.isBlank()) return new ArrayList<>();
        try {
            return OM.readValue(goalSnapshotJson, new TypeReference<List<GoalSnapshotDto>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
