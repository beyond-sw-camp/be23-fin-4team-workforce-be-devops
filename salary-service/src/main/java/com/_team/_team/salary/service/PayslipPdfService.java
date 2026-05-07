package com._team._team.salary.service;

import com._team._team.annotation.Action;
import com._team._team.annotation.Resource;
import com._team._team.dto.BusinessException;
import com._team._team.salary.domain.Payroll;
import com._team._team.salary.domain.PayrollItem;
import com._team._team.salary.domain.enums.ItemType;
import com._team._team.salary.domain.enums.PayrollType;
import com._team._team.salary.repository.PayrollItemRepository;
import com._team._team.salary.repository.PayrollRepository;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 급여대장 기반 급여명세서 PDF 생성
 */
@Slf4j
@Service
public class PayslipPdfService {

    private static final String FONT_RESOURCE = "/fonts/NotoSansKR-Regular.otf";
    private static final String FONT_ENV = "SALARY_PAYSLIP_FONT_PATH";
    private static final String SALARY_READ = Resource.SALARY.name() + ":" + Action.READ.name();

    /** 세금공제 카테고리로 분류할 항목명 (그 외 공제 = 기타공제) */
    private static final Set<String> TAX_DEDUCTION_NAMES = Set.of(
            "소득세", "주민세", "지방소득세"
    );

    /** 정상근무 시간 - 화면과 동일하게 168H 고정 표기 (월 소정근로시간 기반) */
    private static final String STANDARD_WORK_HOURS_LABEL = "168H";

    // 색상
    private static final Color BANNER_BG = new Color(0xEE, 0xF1, 0xFF);   // 라벤더
    private static final Color BANNER_DOT = new Color(0x59, 0x7E, 0xF7);  // 파란 점
    private static final Color SECTION_LINE = new Color(0x0F, 0x17, 0x29); // 섹션 하단 진한 라인 (slate-900)
    private static final Color SOFT_LINE = new Color(0xE2, 0xE8, 0xF0);    // 항목 사이 옅은 라인 (slate-200)
    private static final Color MUTED_TEXT = new Color(0x64, 0x74, 0x8B);   // slate-500
    private static final Color BODY_TEXT = new Color(0x33, 0x41, 0x55);    // slate-700
    private static final Color HEADING_TEXT = new Color(0x0F, 0x17, 0x29); // slate-900
    private static final Color DEDUCTION_RED = new Color(0xEF, 0x44, 0x44);// red-500
    private static final Color BADGE_BG = new Color(0xF1, 0xF5, 0xF9);     // slate-100
    private static final Color BADGE_TEXT = new Color(0x47, 0x55, 0x69);

    private final PayrollRepository payrollRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final RedisTemplate<String, String> permissionRedisTemplate;

    @Autowired
    public PayslipPdfService(
            PayrollRepository payrollRepository,
            PayrollItemRepository payrollItemRepository,
            @Qualifier("permissionInventory") RedisTemplate<String, String> permissionRedisTemplate) {
        this.payrollRepository = payrollRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.permissionRedisTemplate = permissionRedisTemplate;
    }

    @Transactional(readOnly = true)
    public byte[] buildPdf(
            UUID companyId,
            UUID payrollId,
            UUID requesterMemberId,
            UUID requesterPositionId,
            String isSystemAdminHeader) {

        Payroll payroll = payrollRepository
                .findByPayrollIdAndCompanyIdAndDelYn(payrollId, companyId, "N")
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "급여 대장을 찾을 수 없습니다."));

        assertPayslipDownloadAllowed(payroll, requesterMemberId, requesterPositionId, isSystemAdminHeader);

        List<PayrollItem> items = payrollItemRepository
                .findByPayroll_PayrollIdAndDelYnOrderByDisplayOrder(payrollId, "N");

        try {
            return renderPdf(payroll, items);
        } catch (Exception e) {
            log.error("[PayslipPdf] 생성 실패 payrollId={} message={}", payrollId, e.getMessage(), e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "급여명세서 PDF 생성에 실패했습니다: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    /**
     * PermissionAspect 와 동일 규칙: 시스템 관리자 통과, 본인 명세서 통과, 그 외 SALARY:READ 필요
     */
    private void assertPayslipDownloadAllowed(
            Payroll payroll,
            UUID requesterMemberId,
            UUID requesterPositionId,
            String isSystemAdminHeader) {

        if ("YES".equals(isSystemAdminHeader)) {
            return;
        }
        if (payroll.getMemberId().equals(requesterMemberId)) {
            return;
        }
        if (requesterPositionId == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한을 확인할 수 없습니다. 다시 로그인해주세요.");
        }

        String permissionStr = permissionRedisTemplate.opsForValue()
                .get("PERMISSION:" + requesterPositionId);
        if (permissionStr == null || permissionStr.isEmpty()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "권한 정보를 찾을 수 없습니다. 다시 로그인해주세요.");
        }
        boolean hasSalaryRead = List.of(permissionStr.split(",")).stream()
                .anyMatch(p -> p.startsWith(SALARY_READ));
        if (!hasSalaryRead) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "본인 명세서만 다운로드할 수 있습니다.");
        }
    }

    /** 렌더링 */
    private byte[] renderPdf(Payroll payroll, List<PayrollItem> items) throws DocumentException, IOException {
        FontSet fontSet = resolveFontSet();
        BaseFont baseFont = fontSet.baseFont();
        boolean korean = fontSet.korean();

        Font titleFont   = font(baseFont, 18, Font.BOLD, HEADING_TEXT);
        Font metaLabel   = font(baseFont, 9,  Font.NORMAL, MUTED_TEXT);
        Font metaValue   = font(baseFont, 9,  Font.BOLD,   HEADING_TEXT);
        Font bannerLabel = font(baseFont, 10, Font.NORMAL, BODY_TEXT);
        Font bannerVal   = font(baseFont, 12, Font.BOLD,   HEADING_TEXT);
        Font sectionH    = font(baseFont, 12, Font.BOLD,   HEADING_TEXT);
        Font sectionTotalEarn = font(baseFont, 11, Font.BOLD, HEADING_TEXT);
        Font sectionTotalDed  = font(baseFont, 11, Font.BOLD, DEDUCTION_RED);
        Font subGroupH   = font(baseFont, 9,  Font.BOLD,   BODY_TEXT);
        Font itemName    = font(baseFont, 9,  Font.NORMAL, BODY_TEXT);
        Font itemAmount  = font(baseFont, 9,  Font.BOLD,   HEADING_TEXT);
        Font badge       = font(baseFont, 7,  Font.NORMAL, BADGE_TEXT);
        Font emptyHint   = font(baseFont, 8,  Font.NORMAL, MUTED_TEXT);

        NumberFormat nf = NumberFormat.getInstance(Locale.KOREA);

        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, out);
        document.open();

        // 1. 타이틀
        Paragraph title = new Paragraph(korean ? "급여명세서" : "PAYSLIP", titleFont);
        title.setSpacingAfter(14f);
        document.add(title);

        // 2. 메타 영역 (급여 구분 | 근무기간 | 지급일)
        document.add(buildMetaTable(payroll, metaLabel, metaValue, korean));

        // 간격
        document.add(spacer(8f));

        // 3. 라벤더 배너 (정상근무 168H + 실수령액)
        document.add(buildBanner(payroll, bannerLabel, bannerVal, nf, korean));

        // 간격
        document.add(spacer(14f));

        // 4. 2단 (지급내역 / 공제내역)
        List<PayrollItem> earnings = items.stream()
                .filter(it -> it.getItemType() == ItemType.EARNING)
                .sorted(Comparator.comparing(it -> nz(it.getDisplayOrder())))
                .toList();
        List<PayrollItem> taxDeductions = items.stream()
                .filter(it -> it.getItemType() == ItemType.DEDUCTION
                        && TAX_DEDUCTION_NAMES.contains(it.getItemName()))
                .sorted(Comparator.comparing(it -> nz(it.getDisplayOrder())))
                .toList();
        List<PayrollItem> otherDeductions = items.stream()
                .filter(it -> it.getItemType() == ItemType.DEDUCTION
                        && !TAX_DEDUCTION_NAMES.contains(it.getItemName()))
                .sorted(Comparator.comparing(it -> nz(it.getDisplayOrder())))
                .toList();

        PdfPTable layout = new PdfPTable(2);
        layout.setWidthPercentage(100);
        layout.setWidths(new float[]{1f, 1f});
        layout.getDefaultCell().setBorder(0);

        // 좌측 — 지급내역
        PdfPCell leftCell = sectionContainer();
        leftCell.addElement(buildSectionHeader(
                korean ? "지급내역" : "Earnings",
                nf.format(nz(payroll.getTotalPayment())),
                false, sectionH, sectionTotalEarn, korean));
        leftCell.addElement(buildSubGroup(
                korean ? "수당" : "Earnings",
                earnings, korean ? "지급 항목이 없습니다." : "No items.",
                subGroupH, itemName, itemAmount, badge, emptyHint, nf, korean, true));
        layout.addCell(leftCell);

        // 우측 — 공제내역
        PdfPCell rightCell = sectionContainer();
        rightCell.addElement(buildSectionHeader(
                korean ? "공제내역" : "Deductions",
                nf.format(nz(payroll.getTotalDeduction())),
                true, sectionH, sectionTotalDed, korean));
        rightCell.addElement(buildSubGroup(
                korean ? "세금공제" : "Tax",
                taxDeductions, korean ? "세금공제 항목이 없습니다." : "No tax items.",
                subGroupH, itemName, itemAmount, badge, emptyHint, nf, korean, false));
        rightCell.addElement(spacer(6f));
        rightCell.addElement(buildSubGroup(
                korean ? "기타공제" : "Other",
                otherDeductions, korean ? "기타공제 항목이 없습니다." : "No other items.",
                subGroupH, itemName, itemAmount, badge, emptyHint, nf, korean, false));
        layout.addCell(rightCell);

        document.add(layout);

        document.close();
        return out.toByteArray();
    }

    /** 컴포넌트 빌더 */

    /**  좌(급여구분) / 우(근무기간 + 지급일) */
    private PdfPTable buildMetaTable(Payroll payroll, Font label, Font value, boolean korean) throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{4f, 6f});

        // 좌: 급여 구분
        PdfPCell left = blankCell();
        Paragraph p = new Paragraph();
        p.add(new Phrase((korean ? "급여 구분  " : "Payroll type  "), label));
        p.add(new Phrase(payrollTypeLabel(payroll.getPayrollType(), korean), value));
        left.addElement(p);
        t.addCell(left);

        // 우: 근무기간 + 지급일
        PdfPCell right = blankCell();
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph rp = new Paragraph();
        rp.setAlignment(Element.ALIGN_RIGHT);
        rp.add(new Phrase((korean ? "근무기간  " : "Period  "), label));
        rp.add(new Phrase(periodText(payroll) + "    ", value));
        rp.add(new Phrase((korean ? "지급일  " : "Pay date  "), label));
        rp.add(new Phrase(formatDateDot(payroll.getPaidAt() != null
                ? payroll.getPaidAt() : payroll.getPayrollYearMonthDay()), value));
        right.addElement(rp);
        t.addCell(right);

        return t;
    }

    /** 라벤더 배너 (정상근무 168H + 실수령액) */
    private PdfPTable buildBanner(Payroll payroll, Font label, Font value, NumberFormat nf, boolean korean) throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{1f, 1f});

        // 좌측: ● 근무시간  정상근무  168H
        PdfPCell left = bannerCell();
        Paragraph lp = new Paragraph();
        lp.add(new Phrase("● ", font(label.getBaseFont(), 10, Font.BOLD, BANNER_DOT)));
        lp.add(new Phrase((korean ? "근무시간    " : "Hours    "), label));
        lp.add(new Phrase((korean ? "정상근무  " : "Normal  "),
                font(label.getBaseFont(), 9, Font.NORMAL, MUTED_TEXT)));
        lp.add(new Phrase(STANDARD_WORK_HOURS_LABEL, value));
        left.addElement(lp);
        t.addCell(left);

        // 우측: ● 실수령액  X원
        PdfPCell right = bannerCell();
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph rp = new Paragraph();
        rp.setAlignment(Element.ALIGN_RIGHT);
        rp.add(new Phrase("● ", font(label.getBaseFont(), 10, Font.BOLD, BANNER_DOT)));
        rp.add(new Phrase((korean ? "실수령액    " : "Net pay    "), label));
        rp.add(new Phrase(nf.format(nz(payroll.getNetPay())) + (korean ? "원" : ""),
                font(value.getBaseFont(), 14, Font.BOLD, HEADING_TEXT)));
        right.addElement(rp);
        t.addCell(right);

        return t;
    }

    /** 섹션 헤더 (지급내역 / 공제내역) - 진한 하단 라인 */
    private PdfPTable buildSectionHeader(
            String title, String totalText, boolean negative,
            Font titleFont, Font totalFont, boolean korean) throws DocumentException {

        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{1f, 1f});
        t.setSpacingAfter(2f);

        PdfPCell tc = new PdfPCell(new Phrase(title, titleFont));
        tc.setBorder(PdfPCell.BOTTOM);
        tc.setBorderColor(SECTION_LINE);
        tc.setBorderWidthBottom(1.2f);
        tc.setPaddingBottom(6f);
        tc.setPaddingTop(2f);
        tc.setHorizontalAlignment(Element.ALIGN_LEFT);
        t.addCell(tc);

        PdfPCell ac = new PdfPCell(new Phrase((negative ? "-" : "") + totalText
                + (korean ? "원" : ""), totalFont));
        ac.setBorder(PdfPCell.BOTTOM);
        ac.setBorderColor(SECTION_LINE);
        ac.setBorderWidthBottom(1.2f);
        ac.setPaddingBottom(6f);
        ac.setPaddingTop(2f);
        ac.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(ac);

        return t;
    }

    /** 서브 그룹 (수당 / 세금공제 / 기타공제) */
    private PdfPTable buildSubGroup(
            String groupTitle,
            List<PayrollItem> items,
            String emptyText,
            Font groupHFont, Font nameFont, Font amountFont,
            Font badgeFont, Font emptyFont,
            NumberFormat nf, boolean korean, boolean showNonTaxableBadge) {

        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(8f);

        PdfPCell h = new PdfPCell(new Phrase(groupTitle, groupHFont));
        h.setBorder(0);
        h.setPaddingBottom(4f);
        h.setPaddingTop(2f);
        t.addCell(h);

        if (items.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase(emptyText, emptyFont));
            empty.setBorder(0);
            empty.setPaddingLeft(4f);
            empty.setPaddingTop(4f);
            empty.setPaddingBottom(4f);
            t.addCell(empty);
            return t;
        }

        for (PayrollItem it : items) {
            PdfPCell row = new PdfPCell();
            row.setBorder(PdfPCell.BOTTOM);
            row.setBorderColor(SOFT_LINE);
            row.setBorderWidthBottom(0.5f);
            row.setPadding(0);

            PdfPTable inner = new PdfPTable(2);
            inner.setWidthPercentage(100);
            try {
                inner.setWidths(new float[]{6f, 4f});
            } catch (DocumentException ignored) { /* widths is fixed */ }

            // 좌: 항목명 (+ 비과세 뱃지)
            PdfPCell nameCell = new PdfPCell();
            nameCell.setBorder(0);
            nameCell.setPaddingTop(5f);
            nameCell.setPaddingBottom(5f);
            nameCell.setPaddingLeft(2f);

            Paragraph np = new Paragraph();
            np.add(new Phrase(printableText(it.getItemName(), korean), nameFont));
            if (showNonTaxableBadge && "N".equals(it.getIsTaxableYn())) {
                np.add(new Phrase("  ", nameFont));
                Phrase badgePhrase = new Phrase(korean ? " 비과세 " : " Non-tax ", badgeFont);
                np.add(badgePhrase);
            }
            nameCell.addElement(np);
            inner.addCell(nameCell);

            // 우: 금액
            PdfPCell amtCell = new PdfPCell(new Phrase(
                    nf.format(nz(it.getAmount())) + (korean ? "원" : ""), amountFont));
            amtCell.setBorder(0);
            amtCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            amtCell.setPaddingTop(5f);
            amtCell.setPaddingBottom(5f);
            amtCell.setPaddingRight(2f);
            inner.addCell(amtCell);

            row.addElement(inner);
            t.addCell(row);
        }
        return t;
    }

    /** 셀 / 폰트 / 헬퍼 */
    private PdfPCell sectionContainer() {
        PdfPCell c = new PdfPCell();
        c.setBorder(0);
        c.setPadding(0);
        c.setPaddingLeft(0f);
        c.setPaddingRight(12f);
        return c;
    }

    private PdfPCell blankCell() {
        PdfPCell c = new PdfPCell();
        c.setBorder(0);
        c.setPadding(0);
        return c;
    }

    private PdfPCell bannerCell() {
        PdfPCell c = new PdfPCell();
        c.setBorder(0);
        c.setBackgroundColor(BANNER_BG);
        c.setPadding(12f);
        return c;
    }

    private Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(height);
        return p;
    }

    private Font font(BaseFont base, float size, int style, Color color) {
        Font f = new Font(base, size, style);
        f.setColor(color);
        return f;
    }

    /**
     * 근무기간 - targetYearMonth (정산 대상 월) 가 있으면 그 월의 1일 ~ 말일.
     * 없으면 fallback 으로 payrollYearMonthDay 가 속한 월 사용 (구 데이터 호환).
     */
    private String periodText(Payroll payroll) {
        String ym = payroll.getTargetYearMonth();
        if (ym != null && !ym.isBlank()) {
            try {
                java.time.YearMonth ymObj = java.time.YearMonth.parse(ym);
                LocalDate start = ymObj.atDay(1);
                LocalDate end = ymObj.atEndOfMonth();
                return String.format("%04d.%02d.%02d~%02d.%02d",
                        start.getYear(), start.getMonthValue(), start.getDayOfMonth(),
                        end.getMonthValue(), end.getDayOfMonth());
            } catch (Exception ignore) { /* fallback */ }
        }
        LocalDate ymd = payroll.getPayrollYearMonthDay();
        if (ymd == null) return "-";
        LocalDate start = ymd.withDayOfMonth(1);
        LocalDate end = ymd.withDayOfMonth(ymd.lengthOfMonth());
        return String.format("%04d.%02d.%02d~%02d.%02d",
                start.getYear(), start.getMonthValue(), start.getDayOfMonth(),
                end.getMonthValue(), end.getDayOfMonth());
    }

    private String formatDateDot(LocalDate d) {
        if (d == null) return "—";
        return String.format("%04d.%02d.%02d", d.getYear(), d.getMonthValue(), d.getDayOfMonth());
    }

    private String payrollTypeLabel(PayrollType type, boolean korean) {
        if (type == null) return korean ? "—" : "-";
        if (!korean) return type.name();
        return switch (type) {
            case REGULAR_MONTHLY -> "정기급여";
            case PERFORMANCE_BONUS -> "성과급";
            case SPECIAL_BONUS -> "특별상여";
            case RETROACTIVE -> "소급분";
            case RETIREMENT_SETTLEMENT -> "퇴직정산";
        };
    }

    private FontSet resolveFontSet() throws DocumentException, IOException {
        String envPath = System.getenv(FONT_ENV);
        if (envPath != null && !envPath.isBlank()) {
            Path p = Path.of(envPath);
            if (Files.isReadable(p)) {
                byte[] bytes = Files.readAllBytes(p);
                String name = envPath.toLowerCase().endsWith(".ttf") ? "payslip-font.ttf" : "payslip-font.otf";
                BaseFont bf = BaseFont.createFont(
                        name, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
                return new FontSet(bf, true);
            }
            log.warn("[PayslipPdf] SALARY_PAYSLIP_FONT_PATH 가 읽을 수 없습니다: {}", envPath);
        }
        try (InputStream in = PayslipPdfService.class.getResourceAsStream(FONT_RESOURCE)) {
            if (in != null) {
                byte[] bytes = in.readAllBytes();
                BaseFont bf = BaseFont.createFont(
                        "NotoSansKR-Regular.otf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
                return new FontSet(bf, true);
            }
        }
        log.warn("[PayslipPdf] 한글 폰트 없음 — resources{} 또는 {} 를 설정하세요.", FONT_RESOURCE, FONT_ENV);
        BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        return new FontSet(bf, false);
    }

    /** 한글 폰트가 없을 때 WinAnsi 글립 밖 문자로 PDF 생성이 실패할 수 있어 동적 텍스트를 ASCII로 축소 */
    private String printableText(String raw, boolean korean) {
        if (raw == null || raw.isBlank()) return "";
        if (korean) return raw;
        return raw.replaceAll("[^\\x20-\\x7E]", "?");
    }

    /** null 방지 */
    private long nz(Long v) {
        return v == null ? 0L : v;
    }

    /** null 방지 */
    private int nz(Integer v) {
        return v == null ? Integer.MAX_VALUE : v;
    }

    private record FontSet(BaseFont baseFont, boolean korean) {}
}
