package com._team._team.evaluation.report;

import com._team._team.dto.BusinessException;
import com._team._team.evaluation.dto.resdto.ResponseResDto;
import com._team._team.evaluation.service.EvaluationResponseService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 피평가자 본인의 한 시즌 결과를 PDF로 렌더링한다.
 *
 * 사용처: 평가 Hub 의 "리포트 다운로드" 버튼, 내 평가 결과 페이지.
 * member-service 가 제공하는 이름은 EvaluationResponseService 가 이미 주입해 준 값을 사용.
 */
@Slf4j
@Service
public class EvaluationReportService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** PDF 렌더링에 사용하는 한글 폰트 family 이름 (CSS font-family 와 일치해야 함). */
    private static final String KOREAN_FONT_FAMILY = "NotoSansKR";

    /** classpath 내 폰트 위치. 없으면 한글이 네모로 렌더링되므로 반드시 배치 필요. */
    private static final String FONT_REGULAR = "fonts/NotoSansKR-Regular.ttf";
    private static final String FONT_BOLD = "fonts/NotoSansKR-Bold.ttf";

    private final EvaluationResponseService responseService;
    private final ObjectMapper objectMapper;

    public EvaluationReportService(EvaluationResponseService responseService, ObjectMapper objectMapper) {
        this.responseService = responseService;
        this.objectMapper = objectMapper;
    }

    /**
     * 주어진 시즌에서 특정 피평가자의 리포트 PDF 바이트를 반환.
     * 결과가 공개되지 않은 시즌이면 service 레벨에서 400 예외가 난다.
     */
    public byte[] renderMySeasonReport(UUID seasonId, UUID memberId, UUID companyId) {
        List<ResponseResDto> responses = responseService.listMySeasonResult(seasonId, memberId, companyId);
        if (responses.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "해당 시즌에 조회 가능한 평가 결과가 없습니다.");
        }
        boolean published = responses.stream().anyMatch(r -> r.getSeasonResultsPublishedAt() != null);
        if (!published) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "결과 공개 후에만 PDF 리포트를 다운로드할 수 있습니다.");
        }
        String html = buildHtml(responses);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            registerKoreanFonts(builder);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "PDF 생성에 실패했습니다: " + e.getMessage());
        }
    }

    /**
     * classpath 의 한글 TTF 폰트를 PDF 렌더러에 등록.
     * 폰트 파일이 없으면 WARN 로그만 남기고 진행 — 영문은 보이지만 한글은 네모로 렌더된다.
     * 프로젝트 루트 기준 {@code goal-service/src/main/resources/fonts/} 에 TTF 두 개가 있어야 함:
     *   - NotoSansKR-Regular.ttf (weight 400)
     *   - NotoSansKR-Bold.ttf    (weight 700)
     */
    private void registerKoreanFonts(PdfRendererBuilder builder) {
        registerFont(builder, FONT_REGULAR, 400);
        registerFont(builder, FONT_BOLD, 700);
    }

    private void registerFont(PdfRendererBuilder builder, String classpath, int weight) {
        ClassPathResource res = new ClassPathResource(classpath);
        if (!res.exists()) {
            log.warn("[PDF] 한글 폰트 파일이 없습니다 — 한글이 네모로 렌더링됨. path=classpath:{}", classpath);
            return;
        }
        builder.useFont(
                () -> {
                    try {
                        return (InputStream) res.getInputStream();
                    } catch (Exception e) {
                        throw new RuntimeException("폰트 로딩 실패: " + classpath, e);
                    }
                },
                KOREAN_FONT_FAMILY,
                weight,
                BaseRendererBuilder.FontStyle.NORMAL,
                true);
    }

    // ── HTML 조립 ─────────────────────────────────────────────

    private String buildHtml(List<ResponseResDto> responses) {
        ResponseResDto any = responses.get(0);
        String seasonName = safe(any.getSeasonName());
        String publishedAt = any.getSeasonResultsPublishedAt() != null ? any.getSeasonResultsPublishedAt().format(DT) : "-";
        String targetName = responses.stream()
                .map(ResponseResDto::getTargetMemberName)
                .filter(n -> n != null && !n.isBlank())
                .findFirst()
                .orElse("-");

        String finalGrade = responses.stream()
                .map(r -> r.getConfirmedGrade() != null && !r.getConfirmedGrade().isBlank()
                        ? r.getConfirmedGrade()
                        : extractGrade(r.getCalibrationJson()))
                .filter(g -> g != null && !g.isBlank())
                .findFirst()
                .orElse(null);

        // 타인 평가 평균 점수
        List<ResponseResDto> others = responses.stream()
                .filter(r -> !"SELF".equals(r.getEvaluationType() == null ? "" : r.getEvaluationType().name()))
                .toList();
        BigDecimal avgScore = responses.stream()
                .map(ResponseResDto::getFinalScoreSnapshot)
                .filter(s -> s != null)
                .findFirst()
                .orElseGet(() -> averageScore(others));

        // 평가 유형별 그룹핑
        Map<String, List<ResponseResDto>> byType = new LinkedHashMap<>();
        List<ResponseResDto> ordered = new ArrayList<>(responses);
        ordered.sort(Comparator.comparing(r -> r.getEvaluationType() == null ? "" : r.getEvaluationType().name()));
        for (ResponseResDto r : ordered) {
            String t = r.getEvaluationType() == null ? "UNKNOWN" : r.getEvaluationType().name();
            byType.computeIfAbsent(t, k -> new ArrayList<>()).add(r);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />");
        sb.append("<title>Evaluation Report</title>");
        sb.append("<style>");
        // 한글 렌더링: registerKoreanFonts() 에서 등록한 "NotoSansKR" family 를 1순위로.
        sb.append("body { font-family: 'NotoSansKR', sans-serif; color: #1e293b; font-size: 11pt; margin: 28pt; }");
        sb.append("h1 { font-size: 20pt; margin: 0 0 4pt 0; color: #0f172a; }");
        sb.append("h2 { font-size: 13pt; margin: 18pt 0 6pt 0; color: #334155; border-bottom: 1px solid #e2e8f0; padding-bottom: 4pt; }");
        sb.append("p.sub { color: #64748b; font-size: 10pt; margin: 0 0 16pt 0; }");
        sb.append(".hero { background: #eef2ff; border-radius: 8pt; padding: 16pt; margin: 12pt 0; }");
        sb.append(".hero .grade { font-size: 32pt; font-weight: bold; color: #4338ca; }");
        sb.append(".kv { display: table; width: 100%; margin: 6pt 0; }");
        sb.append(".kv .r { display: table-row; }");
        sb.append(".kv .k { display: table-cell; color: #64748b; width: 30%; padding: 3pt 0; }");
        sb.append(".kv .v { display: table-cell; color: #0f172a; padding: 3pt 0; }");
        sb.append(".card { border: 1px solid #e2e8f0; border-radius: 6pt; padding: 10pt 12pt; margin: 8pt 0; }");
        sb.append(".card .head { color: #475569; font-size: 10pt; margin-bottom: 4pt; }");
        sb.append(".card .comment { white-space: pre-wrap; color: #1e293b; font-size: 11pt; margin: 2pt 0; }");
        sb.append(".foot { color: #94a3b8; font-size: 9pt; margin-top: 24pt; text-align: center; }");
        sb.append("</style></head><body>");

        sb.append("<h1>평가 결과 리포트</h1>");
        sb.append("<p class=\"sub\">").append(esc(seasonName)).append(" · ").append(esc(targetName)).append(" · 발행 ").append(esc(publishedAt)).append("</p>");

        // 히어로 (최종 등급)
        sb.append("<div class=\"hero\">");
        sb.append("<div style=\"color:#6366f1;font-size:9pt;letter-spacing:1pt;\">CURRENT STANDING</div>");
        if (finalGrade != null) {
            sb.append("<div class=\"grade\">Grade ").append(esc(finalGrade)).append("</div>");
        } else {
            sb.append("<div class=\"grade\">미부여</div>");
        }
        sb.append("<div style=\"color:#475569;font-size:10pt;\">");
        if (avgScore != null) {
            sb.append("타인 평가 평균 점수 ").append(avgScore.toPlainString()).append("점 · ");
        }
        sb.append("받은 평가 ").append(others.size()).append("건");
        sb.append("</div></div>");

        // 요약
        sb.append("<h2>요약</h2>");
        sb.append("<div class=\"kv\">");
        sb.append("<div class=\"r\"><div class=\"k\">시즌</div><div class=\"v\">").append(esc(seasonName)).append("</div></div>");
        sb.append("<div class=\"r\"><div class=\"k\">피평가자</div><div class=\"v\">").append(esc(targetName)).append("</div></div>");
        sb.append("<div class=\"r\"><div class=\"k\">최종 등급</div><div class=\"v\">").append(finalGrade == null ? "-" : esc(finalGrade)).append("</div></div>");
        sb.append("<div class=\"r\"><div class=\"k\">결과 공개 시각</div><div class=\"v\">").append(esc(publishedAt)).append("</div></div>");
        sb.append("</div>");

        // 유형별 피드백
        for (Map.Entry<String, List<ResponseResDto>> entry : byType.entrySet()) {
            String typeLabel = evalTypeKoLabel(entry.getKey());
            sb.append("<h2>").append(esc(typeLabel)).append(" (" + entry.getValue().size() + "건)</h2>");
            for (ResponseResDto r : entry.getValue()) {
                sb.append("<div class=\"card\">");
                String evaluator = (r.getEvaluatorName() != null && !r.getEvaluatorName().isBlank())
                        ? r.getEvaluatorName() : "익명";
                String score = r.getNormalizedScore() != null ? r.getNormalizedScore().toPlainString() + "점" : "-";
                sb.append("<div class=\"head\">평가자: ").append(esc(evaluator)).append(" · 점수: ").append(esc(score)).append("</div>");
                List<String> comments = extractComments(r.getAnswersJson());
                if (comments.isEmpty()) {
                    sb.append("<div class=\"comment\" style=\"color:#94a3b8;\">서술형 피드백이 없습니다.</div>");
                } else {
                    for (String c : comments) {
                        sb.append("<div class=\"comment\">").append(esc(c)).append("</div>");
                    }
                }
                sb.append("</div>");
            }
        }

        sb.append("<div class=\"foot\">생성일시 ").append(LocalDateTime.now().format(DT)).append("</div>");
        sb.append("</body></html>");
        return sb.toString();
    }

    // ── 유틸 ──────────────────────────────────────────────────

    private String extractGrade(String calibrationJson) {
        if (calibrationJson == null || calibrationJson.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(calibrationJson);
            JsonNode adj = node.get("adjustedGrade");
            if (adj != null && !adj.isNull() && !adj.asText().isBlank()) return adj.asText();
            JsonNode orig = node.get("originalGrade");
            if (orig != null && !orig.isNull() && !orig.asText().isBlank()) return orig.asText();
        } catch (Exception ignore) {
        }
        return null;
    }

    private List<String> extractComments(String answersJson) {
        List<String> out = new ArrayList<>();
        if (answersJson == null || answersJson.isBlank()) return out;
        try {
            JsonNode root = objectMapper.readTree(answersJson);
            JsonNode arr = root.isArray() ? root : root.get("items");
            if (arr != null && arr.isArray()) {
                for (JsonNode item : arr) {
                    JsonNode t = item.get("textValue");
                    if (t != null && !t.isNull() && !t.asText().isBlank()) {
                        out.add(t.asText().trim());
                    }
                }
            }
        } catch (Exception ignore) {
        }
        return out;
    }

    private BigDecimal averageScore(List<ResponseResDto> list) {
        List<BigDecimal> scores = new ArrayList<>();
        for (ResponseResDto r : list) {
            if (r.getNormalizedScore() != null) scores.add(r.getNormalizedScore());
        }
        if (scores.isEmpty()) return null;
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal s : scores) sum = sum.add(s);
        return sum.divide(BigDecimal.valueOf(scores.size()), 1, RoundingMode.HALF_UP);
    }

    private String evalTypeKoLabel(String t) {
        return switch (t) {
            case "SELF" -> "자기 평가";
            case "PEER" -> "동료 평가";
            case "UPWARD" -> "상향 평가";
            case "DOWNWARD" -> "하향 평가";
            default -> t;
        };
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
