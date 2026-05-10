package com._team._team.contract.service;

import com._team._team.contract.domain.Contract;
import com._team._team.contract.domain.ContractParty;
import com._team._team.contract.domain.enums.PartyRole;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractPdfService {

    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_DOT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd(E)", Locale.KOREAN);
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final NumberFormat NUMBER_FMT = NumberFormat.getInstance(Locale.KOREA);

    // 색상 (프론트와 동일)
    private static final Color HEADER_BG = new Color(0xF8, 0xF9, 0xFA);   // 연한 회색 배경
    private static final Color BORDER_COLOR = new Color(0xDE, 0xE2, 0xE6); // 테두리
    private static final Color HEADING_TEXT = new Color(0x21, 0x25, 0x29);  // 진한 텍스트
    private static final Color BODY_TEXT = new Color(0x49, 0x50, 0x57);     // 본문 텍스트
    private static final Color STATUS_GREEN = new Color(0x28, 0xA7, 0x45);  // 완료 상태
    private static final Color SIGN_BG = new Color(0xF1, 0xF3, 0xF5);      // 서명란 배경

    // 금액 관련 필드 판별용
    private static final Set<String> MONEY_KEYS = Set.of(
            "baseSalary", "newSalary", "salary", "annualSalary", "monthlyPay"
    );

    public byte[] buildPdf(Contract contract, List<ContractParty> parties) {
        try {
            return renderPdf(contract, parties);
        } catch (Exception e) {
            log.error("[ContractPdf] PDF 생성 실패. contractId={}", contract.getContractId(), e);
            throw new RuntimeException("계약서 PDF 생성 실패", e);
        }
    }

    private byte[] renderPdf(Contract contract, List<ContractParty> parties) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(document, baos);
        document.open();

        BaseFont baseFont = loadKoreanFont();
        Font titleFont = new Font(baseFont, 20, Font.BOLD, HEADING_TEXT);
        Font headerLabelFont = new Font(baseFont, 9, Font.BOLD, BODY_TEXT);
        Font headerValueFont = new Font(baseFont, 9, Font.NORMAL, HEADING_TEXT);
        Font sectionFont = new Font(baseFont, 11, Font.BOLD, HEADING_TEXT);
        Font labelFont = new Font(baseFont, 9, Font.BOLD, BODY_TEXT);
        Font valueFont = new Font(baseFont, 9, Font.NORMAL, HEADING_TEXT);
        Font smallFont = new Font(baseFont, 7, Font.NORMAL, Color.GRAY);
        Font statusFont = new Font(baseFont, 8, Font.BOLD, STATUS_GREEN);
        Font signLabelFont = new Font(baseFont, 8, Font.BOLD, BODY_TEXT);
        Font signValueFont = new Font(baseFont, 8, Font.NORMAL, BODY_TEXT);
        Font footerFont = new Font(baseFont, 8, Font.NORMAL, Color.GRAY);

        String templateName = contract.getContractTemplate().getTemplateName();

        // 서명일
        String signedDate = parties.stream()
                .filter(p -> p.getSignedAt() != null)
                .map(p -> p.getSignedAt().format(DATE_DOT_FMT))
                .reduce((a, b) -> b)
                .orElse("-");

        // ─── 제목 ───
        Paragraph title = new Paragraph(templateName, titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20f);
        document.add(title);

        // ─── 상단: 기안 정보(좌) + 서명란(우) ───
        PdfPTable topLayout = new PdfPTable(2);
        topLayout.setWidthPercentage(100);
        topLayout.setWidths(new float[]{5f, 5f});

        // 좌측: 기안 정보 테이블
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{3f, 7f});

        addInfoRow(infoTable, "기안자", contract.getEmployeeName(), headerLabelFont, headerValueFont);
        addInfoRow(infoTable, "소속", contract.getOrganizationName() != null ? contract.getOrganizationName() : "-", headerLabelFont, headerValueFont);
        addInfoRow(infoTable, "기안일", signedDate, headerLabelFont, headerValueFont);
        addInfoRow(infoTable, "문서번호", contract.getContractNumber() != null ? contract.getContractNumber() : "-", headerLabelFont, headerValueFont);

        PdfPCell leftCell = new PdfPCell(infoTable);
        leftCell.setBorder(0);
        leftCell.setPaddingRight(10f);
        topLayout.addCell(leftCell);

        // 우측: 서명란
        PdfPTable signTable = buildSignatureTable(contract, parties, signLabelFont, signValueFont, smallFont);
        PdfPCell rightCell = new PdfPCell(signTable);
        rightCell.setBorder(0);
        rightCell.setPaddingLeft(10f);
        topLayout.addCell(rightCell);

        document.add(topLayout);
        document.add(spacer(15f));

        // ─── 계약 내용 테이블 ───
        Map<String, Object> content = objectMapper.readValue(
                contract.getContentJson(),
                new TypeReference<Map<String, Object>>() {});
        Map<String, String> labelMap = buildLabelMap(contract.getFormSchemaSnapshot());

        PdfPTable contentTable = new PdfPTable(2);
        contentTable.setWidthPercentage(100);
        contentTable.setWidths(new float[]{3f, 7f});

        // 문서번호 행
        addContentRow(contentTable, "문서번호",
                contract.getContractNumber() != null ? contract.getContractNumber() : "-",
                labelFont, valueFont);

        // 상태 행
        addStatusRow(contentTable, "상태", "완료", labelFont, statusFont);

        // contentJson 데이터 행
        for (Map.Entry<String, Object> entry : content.entrySet()) {
            String label = labelMap.getOrDefault(entry.getKey(), entry.getKey());
            String value = formatValue(entry.getKey(), entry.getValue());
            addContentRow(contentTable, label, value, labelFont, valueFont);
        }

        document.add(contentTable);
        document.add(spacer(20f));

        // ─── 인감 이미지 ───
        if (contract.getSealImageUrl() != null) {
            try {
                Image sealImage = Image.getInstance(new URL(contract.getSealImageUrl()));
                sealImage.scaleToFit(80, 80);
                sealImage.setAlignment(Element.ALIGN_RIGHT);
                document.add(sealImage);
            } catch (Exception e) {
                log.warn("[ContractPdf] 인감 이미지 로드 실패. url={}", contract.getSealImageUrl());
            }
        }

        // ─── 하단 안내 ───
        Paragraph footer = new Paragraph(
                "본 문서는 전자서명법에 의거하여 전자적으로 체결된 계약서입니다.",
                footerFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(30f);
        document.add(footer);

        document.close();
        return baos.toByteArray();
    }

    // ─── 기안 정보 행 ───
    private void addInfoRow(PdfPTable table, String label, String value,
                            Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBackgroundColor(HEADER_BG);
        labelCell.setBorderColor(BORDER_COLOR);
        labelCell.setPadding(8f);
        labelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorderColor(BORDER_COLOR);
        valueCell.setPadding(8f);
        table.addCell(valueCell);
    }

    // ─── 서명란 테이블 (프론트 우상단과 동일) ───
    private PdfPTable buildSignatureTable(Contract contract, List<ContractParty> parties,
                                          Font labelFont, Font valueFont, Font smallFont) throws DocumentException {

        ContractParty employee = parties.stream()
                .filter(p -> p.getPartyRole() == PartyRole.EMPLOYEE).findFirst().orElse(null);
        ContractParty company = parties.stream()
                .filter(p -> p.getPartyRole() == PartyRole.COMPANY).findFirst().orElse(null);

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2f, 4f, 4f});

        // 헤더: 빈칸 | 직원 | 회사
        addSignCell(table, "", labelFont, SIGN_BG, Element.ALIGN_CENTER);
        addSignCell(table, "직원", labelFont, SIGN_BG, Element.ALIGN_CENTER);
        addSignCell(table, "회사", labelFont, SIGN_BG, Element.ALIGN_CENTER);

        // 서명 행: "서명" | 직원이름 + 서명이미지 | 회사이름 + 서명이미지
        addSignCell(table, "서명", labelFont, SIGN_BG, Element.ALIGN_CENTER);

        // 직원 서명 셀
        PdfPCell empCell = new PdfPCell();
        empCell.setBorderColor(BORDER_COLOR);
        empCell.setPadding(5f);
        empCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        if (employee != null) {
            Paragraph empName = new Paragraph(contract.getEmployeeName(), valueFont);
            empName.setAlignment(Element.ALIGN_CENTER);
            empCell.addElement(empName);
            // 서명 이미지
            if (employee.getSignatureImageUrl() != null) {
                try {
                    Image sig = Image.getInstance(new URL(employee.getSignatureImageUrl()));
                    sig.scaleToFit(50, 30);
                    sig.setAlignment(Element.ALIGN_CENTER);
                    empCell.addElement(sig);
                } catch (Exception e) {
                    Paragraph placeholder = new Paragraph("직원 서명", smallFont);
                    placeholder.setAlignment(Element.ALIGN_CENTER);
                    empCell.addElement(placeholder);
                }
            }
            if (employee.getSignedAt() != null) {
                Paragraph empDate = new Paragraph(employee.getSignedAt().format(DATETIME_FMT), smallFont);
                empDate.setAlignment(Element.ALIGN_CENTER);
                empCell.addElement(empDate);
            }
        }
        table.addCell(empCell);

        // 회사 서명 셀
        PdfPCell comCell = new PdfPCell();
        comCell.setBorderColor(BORDER_COLOR);
        comCell.setPadding(5f);
        comCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        if (company != null) {
            Paragraph comName = new Paragraph(
                    contract.getOrganizationName() != null ? contract.getOrganizationName() : "회사",
                    valueFont);
            comName.setAlignment(Element.ALIGN_CENTER);
            comCell.addElement(comName);
            // 회사 서명/인감 이미지
            if (contract.getSealImageUrl() != null) {
                try {
                    Image seal = Image.getInstance(new URL(contract.getSealImageUrl()));
                    seal.scaleToFit(50, 30);
                    seal.setAlignment(Element.ALIGN_CENTER);
                    comCell.addElement(seal);
                } catch (Exception ignored) {}
            }
            if (company.getSignedAt() != null) {
                Paragraph comDate = new Paragraph(company.getSignedAt().format(DATETIME_FMT), smallFont);
                comDate.setAlignment(Element.ALIGN_CENTER);
                comCell.addElement(comDate);
            }
        }
        table.addCell(comCell);

        return table;
    }

    private void addSignCell(PdfPTable table, String text, Font font, Color bgColor, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bgColor);
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(6f);
        cell.setHorizontalAlignment(align);
        table.addCell(cell);
    }

    // ─── 내용 테이블 행 ───
    private void addContentRow(PdfPTable table, String label, String value,
                               Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBackgroundColor(HEADER_BG);
        labelCell.setBorderColor(BORDER_COLOR);
        labelCell.setPadding(10f);
        labelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorderColor(BORDER_COLOR);
        valueCell.setPadding(10f);
        table.addCell(valueCell);
    }

    // ─── 상태 행 (완료 뱃지) ───
    private void addStatusRow(PdfPTable table, String label, String status,
                              Font labelFont, Font statusFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBackgroundColor(HEADER_BG);
        labelCell.setBorderColor(BORDER_COLOR);
        labelCell.setPadding(10f);
        labelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(status, statusFont));
        valueCell.setBorderColor(BORDER_COLOR);
        valueCell.setPadding(10f);
        table.addCell(valueCell);
    }

    // ─── 값 포맷팅 (금액 콤마, null 처리) ───
    private String formatValue(String key, Object value) {
        if (value == null) return "—";
        String str = value.toString();
        if (str.isBlank()) return "—";

        // 금액 필드면 콤마 포맷
        if (MONEY_KEYS.contains(key)) {
            try {
                long num = Long.parseLong(str);
                return NUMBER_FMT.format(num);
            } catch (NumberFormatException ignored) {}
        }

        return str;
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
                    String fieldLabel = (String) field.get("label");
                    if (key != null && fieldLabel != null) {
                        map.put(key, fieldLabel);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[ContractPdf] formSchema 파싱 실패", e);
        }
        return map;
    }

    private Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(height);
        return p;
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
            log.warn("[ContractPdf] 한글 폰트 로드 실패, Helvetica로 대체", e);
            try {
                return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            } catch (Exception ex) {
                throw new RuntimeException("폰트 로드 불가", ex);
            }
        }
    }
}
