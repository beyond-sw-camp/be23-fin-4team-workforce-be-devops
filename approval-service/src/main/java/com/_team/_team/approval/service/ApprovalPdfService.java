package com._team._team.approval.service;

import com._team._team.approval.domain.Approval;
import com._team._team.approval.domain.ApprovalRequest;
import com._team._team.approval.domain.enums.LineStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalPdfService {

    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    public byte[] buildPdf(ApprovalRequest request, List<Approval> approvalLines) {
        try {
            return renderPdf(request, approvalLines);
        } catch (Exception e) {
            log.error("[ApprovalPdf] PDF 생성 실패. requestId={}", request.getRequestId(), e);
            throw new RuntimeException("결재 문서 PDF 생성 실패", e);
        }
    }

    private byte[] renderPdf(ApprovalRequest request, List<Approval> approvalLines) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        PdfWriter.getInstance(document, baos);
        document.open();

        BaseFont baseFont = loadKoreanFont();
        Font titleFont = new Font(baseFont, 18, Font.BOLD);
        Font headerFont = new Font(baseFont, 12, Font.BOLD);
        Font bodyFont = new Font(baseFont, 10, Font.NORMAL);
        Font smallFont = new Font(baseFont, 8, Font.NORMAL, Color.GRAY);

        // ─── 제목 ───
        String documentName = request.getApprovalDocument().getDocumentName();
        Paragraph title = new Paragraph(documentName, titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20f);
        document.add(title);

        // ─── 문서번호 & 기안일 ───
        if (request.getDocumentNumber() != null) {
            document.add(new Paragraph("문서번호: " + request.getDocumentNumber(), bodyFont));
        }
        document.add(new Paragraph("기안일: " + request.getCreatedAt().format(DATE_FMT), bodyFont));
        document.add(new Paragraph("기안자: " + request.getRequesterName()
                + " (" + request.getRequesterOrganizationName() + ")", bodyFont));

        Paragraph statusPara = new Paragraph("상태: " + requestStatusLabel(request.getRequestStatus().name()), bodyFont);
        statusPara.setSpacingAfter(15f);
        document.add(statusPara);

        // ─── 결재선 테이블 ───
        document.add(new Paragraph("결재선", headerFont));
        document.add(buildApprovalLineTable(approvalLines, bodyFont));

        document.add(Chunk.NEWLINE);

        // ─── 문서 내용 (contentJson 기반) ───
        document.add(new Paragraph("문서 내용", headerFont));
        document.add(buildContentTable(request, bodyFont));

        document.add(Chunk.NEWLINE);

        // ─── 하단 안내 ───
        Paragraph footer = new Paragraph(
                "본 문서는 전자결재 시스템을 통해 생성된 문서입니다.",
                smallFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(30f);
        document.add(footer);

        document.close();
        return baos.toByteArray();
    }

    private PdfPTable buildApprovalLineTable(List<Approval> approvalLines, Font font) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 3, 2, 3});
        table.setSpacingBefore(5f);
        table.setSpacingAfter(10f);

        addCell(table, "순서", font, true);
        addCell(table, "결재자", font, true);
        addCell(table, "상태", font, true);
        addCell(table, "처리일시", font, true);

        for (Approval line : approvalLines) {
            addCell(table, String.valueOf(line.getStepOrder()), font, false);
            String name = line.getApproverName() != null
                    ? line.getApproverName()
                    : line.getApproverMemberId().toString().substring(0, 8) + "...";
            addCell(table, name, font, false);
            addCell(table, lineStatusLabel(line.getApprovalStatus()), font, false);
            addCell(table, line.getActedAt() != null
                    ? line.getActedAt().format(DATETIME_FMT)
                    : "-", font, false);
        }
        return table;
    }

    private PdfPTable buildContentTable(ApprovalRequest request, Font font) throws Exception {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3, 7});
        table.setSpacingBefore(5f);
        table.setSpacingAfter(10f);

        Map<String, Object> content = objectMapper.readValue(
                request.getContentJson(),
                new TypeReference<Map<String, Object>>() {});

        Map<String, String> labelMap = buildLabelMap(request.getFormSchemaSnapshot());

        for (Map.Entry<String, Object> entry : content.entrySet()) {
            String label = labelMap.getOrDefault(entry.getKey(), entry.getKey());
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            addCell(table, label, font, true);
            addCell(table, value, font, false);
        }
        return table;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> buildLabelMap(String formSchemaSnapshot) {
        Map<String, String> map = new java.util.HashMap<>();
        if (formSchemaSnapshot == null) return map;
        try {
            Map<String, Object> schema = objectMapper.readValue(
                    formSchemaSnapshot, new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> fields = (List<Map<String, Object>>) schema.get("fields");
            if (fields != null) {
                for (Map<String, Object> field : fields) {
                    String key = (String) field.getOrDefault("key", field.get("name"));
                    String label = (String) field.get("label");
                    if (key != null && label != null) {
                        map.put(key, label);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[ApprovalPdf] formSchema 파싱 실패", e);
        }
        return map;
    }

    private String lineStatusLabel(LineStatus status) {
        if (status == null) return "-";
        return switch (status) {
            case WAITING -> "대기";
            case PENDING -> "결재중";
            case APPROVED -> "승인";
            case REJECTED -> "반려";
            case CANCELED -> "취소";
        };
    }

    private String requestStatusLabel(String status) {
        return switch (status) {
            case "DRAFT" -> "임시저장";
            case "WAIT" -> "대기";
            case "PENDING" -> "진행중";
            case "APPROVED" -> "승인완료";
            case "REJECTED" -> "반려";
            case "CANCELED" -> "취소";
            default -> status;
        };
    }

    private void addCell(PdfPTable table, String text, Font font, boolean isHeader) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        if (isHeader) {
            cell.setBackgroundColor(new Color(238, 241, 255));
        }
        cell.setBorderColor(new Color(200, 200, 200));
        table.addCell(cell);
    }

    private BaseFont loadKoreanFont() {
        try {
            ClassPathResource resource = new ClassPathResource("fonts/NotoSansKR-Regular.otf");
            byte[] fontBytes;
            try (InputStream is = resource.getInputStream()) {
                fontBytes = is.readAllBytes();
            }
            return BaseFont.createFont("NotoSansKR-Regular.otf", BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED, true, fontBytes, null);
        } catch (Exception e) {
            log.warn("[ApprovalPdf] 한글 폰트 로드 실패, Helvetica로 대체", e);
            try {
                return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            } catch (Exception ex) {
                throw new RuntimeException("폰트 로드 불가", ex);
            }
        }
    }

}
