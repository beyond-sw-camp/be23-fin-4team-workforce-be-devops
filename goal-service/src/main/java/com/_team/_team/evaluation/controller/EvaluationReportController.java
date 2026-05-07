package com._team._team.evaluation.controller;

import com._team._team.evaluation.report.EvaluationReportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/evaluation/seasons")
public class EvaluationReportController {

    private final EvaluationReportService reportService;

    public EvaluationReportController(EvaluationReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/{seasonId}/reports/me.pdf")
    public ResponseEntity<byte[]> downloadMySeasonReport(
            @PathVariable UUID seasonId,
            @RequestHeader("X-User-UUID") String memberId,
            @RequestHeader("X-User-CompanyId") String companyId) {
        byte[] pdf = reportService.renderMySeasonReport(
                seasonId,
                UUID.fromString(memberId),
                UUID.fromString(companyId)
        );
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("evaluation-report-" + seasonId + ".pdf", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(pdf);
    }
}
