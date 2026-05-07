package com._team._team.salary.service;

import com._team._team.salary.domain.Payroll;
import com._team._team.salary.domain.PayrollItem;
import com._team._team.salary.domain.enums.ItemType;
import com._team._team.salary.dto.resdto.TaxSummaryResDto;
import com._team._team.salary.feignClients.MemberFeignClient;
import com._team._team.salary.feignClients.dto.MemberResDto;
import com._team._team.salary.repository.PayrollRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// 회사 월 단위 급여대장 엑셀 다운로드
// SXSSF 스트리밍 메모리 효율 동적 컬럼 itemName 기준
@Slf4j
@Service
public class PayrollExportService {

    private static final int SXSSF_KEEP_IN_MEMORY = 100;
    private static final String STATUS_DRAFT_KO = "작성중";
    private static final String STATUS_CONFIRMED_KO = "확정";
    private static final String STATUS_PAID_KO = "지급완료";

    private final PayrollRepository payrollRepository;
    private final TaxSummaryService taxSummaryService;
    private final CachedMemberLookupService cachedMemberLookup;

    @Autowired
    public PayrollExportService(PayrollRepository payrollRepository,
                                TaxSummaryService taxSummaryService,
                                CachedMemberLookupService cachedMemberLookup) {
        this.payrollRepository = payrollRepository;
        this.taxSummaryService = taxSummaryService;
        this.cachedMemberLookup = cachedMemberLookup;
    }

    @Transactional(readOnly = true)
    public byte[] exportMonthlyXlsx(UUID companyId, YearMonth ym) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        // QueryDSL 기반 동적 검색 호출 (관리자 목록과 동일 메서드 공유)
        List<Payroll> payrolls = payrollRepository.searchAdminListInMonth(
                companyId, from, to, null, null, null);
        Map<UUID, MemberResDto> memberMap = fetchMemberMap(companyId);

        // 데이터 없으면 EARNING DEDUCTION 헤더 동적 추출 불가
        // 회사 SalaryItemTemplate 가 없을 수도 있어 고정 컬럼만으로 구성
        List<String> earningHeaders = collectItemNames(payrolls, ItemType.EARNING);
        List<String> deductionHeaders = collectItemNames(payrolls, ItemType.DEDUCTION);

        SXSSFWorkbook wb = new SXSSFWorkbook(SXSSF_KEEP_IN_MEMORY);
        try {
            wb.setCompressTempFiles(true);
            Sheet sheet = wb.createSheet(ym.toString());

            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle moneyStyle = buildMoneyStyle(wb);

            int rowIdx = 0;
            int col = 0;

            // 헤더 항상 작성
            Row header = sheet.createRow(rowIdx++);
            String[] fixedHeaders = {"사번", "이름", "부서", "정산 대상 월", "지급일", "상태"};
            for (String h : fixedHeaders) {
                Cell c = header.createCell(col++);
                c.setCellValue(h);
                c.setCellStyle(headerStyle);
            }
            for (String name : earningHeaders) {
                Cell c = header.createCell(col++);
                c.setCellValue(name);
                c.setCellStyle(headerStyle);
            }
            for (String name : deductionHeaders) {
                Cell c = header.createCell(col++);
                c.setCellValue(name);
                c.setCellStyle(headerStyle);
            }
            String[] tailHeaders = {"총지급", "총공제", "실수령"};
            for (String h : tailHeaders) {
                Cell c = header.createCell(col++);
                c.setCellValue(h);
                c.setCellStyle(headerStyle);
            }

            // 데이터 행
            if (payrolls.isEmpty()) {
                log.info("[PAYROLL-EXPORT] 데이터 없음 헤더만 반환 companyId={} ym={}", companyId, ym);
                // 안내 문구 첫 셀에만 적기
                Row noDataRow = sheet.createRow(rowIdx++);
                noDataRow.createCell(0).setCellValue("해당 월 급여대장 데이터가 없습니다.");
            } else {
                for (Payroll p : payrolls) {
                    Row row = sheet.createRow(rowIdx++);
                    MemberResDto m = memberMap.get(p.getMemberId());
                    col = 0;
                    row.createCell(col++).setCellValue(
                            m != null && m.getSabun() != null ? m.getSabun() : "");
                    row.createCell(col++).setCellValue(
                            m != null && m.getName() != null ? m.getName() : "");
                    row.createCell(col++).setCellValue(
                            m != null && m.getOrganizationName() != null ? m.getOrganizationName() : "");
                    // 정산 대상 월 (YYYY-MM) - targetYearMonth 우선, 없으면 지급일 기준 fallback
                    String targetYm = p.getTargetYearMonth() != null
                            ? p.getTargetYearMonth()
                            : YearMonth.from(p.getPayrollYearMonthDay()).toString();
                    row.createCell(col++).setCellValue(targetYm);
                    row.createCell(col++).setCellValue(p.getPayrollYearMonthDay().toString());
                    row.createCell(col++).setCellValue(statusKorean(p.getPayrollStatus().name()));

                    Map<String, Long> itemMap = p.getPayrollItemList().stream()
                            .filter(it -> "N".equals(it.getDelYn()))
                            .collect(Collectors.toMap(
                                    PayrollItem::getItemName,
                                    PayrollItem::getAmount,
                                    (a, b) -> a));

                    for (String name : earningHeaders) col = writeMoney(row, col, itemMap.get(name), moneyStyle);
                    for (String name : deductionHeaders) col = writeMoney(row, col, itemMap.get(name), moneyStyle);

                    col = writeMoney(row, col, p.getTotalPayment(), moneyStyle);
                    col = writeMoney(row, col, p.getTotalDeduction(), moneyStyle);
                    col = writeMoney(row, col, p.getNetPay(), moneyStyle);
                }
            }

            // 헤더 행 고정 + 자동 필터 (행이 1개 이상일 때만)
            sheet.createFreezePane(0, 1);
            int totalCols = fixedHeaders.length + earningHeaders.size() + deductionHeaders.size() + tailHeaders.length;
            if (rowIdx > 1) {
                sheet.setAutoFilter(new CellRangeAddress(0, rowIdx - 1, 0, totalCols - 1));
            }
            for (int i = 0; i < totalCols; i++) sheet.setColumnWidth(i, 4000);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("엑셀 생성 실패", e);
        } finally {
            wb.dispose();
            try { wb.close(); } catch (IOException ignore) {}
        }
    }

    // 세금 4대보험 월별 집계 엑셀 신고용 자료
    @Transactional(readOnly = true)
    public byte[] exportTaxSummaryXlsx(UUID companyId, YearMonth ym) {
        TaxSummaryResDto data = taxSummaryService.getMonthlySummary(companyId, ym);

        SXSSFWorkbook wb = new SXSSFWorkbook(SXSSF_KEEP_IN_MEMORY);
        try {
            wb.setCompressTempFiles(true);
            Sheet sheet = wb.createSheet("세금·4대보험_" + ym);

            CellStyle titleStyle = buildTitleStyle(wb);
            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle moneyStyle = buildMoneyStyle(wb);
            CellStyle totalStyle = buildTotalStyle(wb);

            int rowIdx = 0;

            // 1. 메타 정보 (3행)
            Row title = sheet.createRow(rowIdx++);
            Cell titleCell = title.createCell(0);
            titleCell.setCellValue("세금·4대보험 월별 집계");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 3));

            Row meta1 = sheet.createRow(rowIdx++);
            meta1.createCell(0).setCellValue("연월");
            meta1.createCell(1).setCellValue(data.getYearMonth());

            Row meta2 = sheet.createRow(rowIdx++);
            meta2.createCell(0).setCellValue("대상 직원");
            meta2.createCell(1).setCellValue(data.getMemberCount() + "명");

            rowIdx++; // 빈 행

            // 2. 4대보험 섹션
            Row sec1 = sheet.createRow(rowIdx++);
            Cell sec1Cell = sec1.createCell(0);
            sec1Cell.setCellValue("[4대보험]");
            sec1Cell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 3));

            Row insHeader = sheet.createRow(rowIdx++);
            String[] insColHeaders = {"항목", "직원 부담 (정확)", "회사 부담 (추정)", "소계"};
            for (int i = 0; i < insColHeaders.length; i++) {
                Cell c = insHeader.createCell(i);
                c.setCellValue(insColHeaders[i]);
                c.setCellStyle(headerStyle);
            }

            // 4대보험 행들
            rowIdx = writeInsuranceRow(sheet, rowIdx, "국민연금",
                    data.getNationalPension(), data.getNationalPensionEmployer(), moneyStyle);
            rowIdx = writeInsuranceRow(sheet, rowIdx, "건강보험",
                    data.getHealthInsurance(), data.getHealthInsuranceEmployer(), moneyStyle);
            rowIdx = writeInsuranceRow(sheet, rowIdx, "장기요양보험",
                    data.getLongTermCare(), data.getLongTermCareEmployer(), moneyStyle);
            rowIdx = writeInsuranceRow(sheet, rowIdx, "고용보험",
                    data.getEmploymentInsurance(), data.getEmploymentInsuranceEmployer(), moneyStyle);
            rowIdx = writeInsuranceRow(sheet, rowIdx, "산재보험",
                    0L, data.getIndustrialAccidentEmployer(), moneyStyle);

            // 4대보험 합계
            Row insTotal = sheet.createRow(rowIdx++);
            insTotal.createCell(0).setCellValue("4대보험 합계");
            insTotal.getCell(0).setCellStyle(totalStyle);
            writeMoneyCell(insTotal, 1, data.getFourInsuranceTotal(), totalStyle);
            writeMoneyCell(insTotal, 2, data.getFourInsuranceEmployerTotal(), totalStyle);
            writeMoneyCell(insTotal, 3,
                    data.getFourInsuranceTotal() + data.getFourInsuranceEmployerTotal(), totalStyle);

            rowIdx++; // 빈 행

            // 3. 원천세 섹션
            Row sec2 = sheet.createRow(rowIdx++);
            Cell sec2Cell = sec2.createCell(0);
            sec2Cell.setCellValue("[원천세]");
            sec2Cell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 3));

            Row whHeader = sheet.createRow(rowIdx++);
            whHeader.createCell(0).setCellValue("항목");
            whHeader.createCell(1).setCellValue("징수 금액");
            whHeader.getCell(0).setCellStyle(headerStyle);
            whHeader.getCell(1).setCellStyle(headerStyle);

            Row incomeRow = sheet.createRow(rowIdx++);
            incomeRow.createCell(0).setCellValue("소득세");
            writeMoneyCell(incomeRow, 1, data.getIncomeTax(), moneyStyle);

            Row localRow = sheet.createRow(rowIdx++);
            localRow.createCell(0).setCellValue("지방소득세");
            writeMoneyCell(localRow, 1, data.getLocalIncomeTax(), moneyStyle);

            Row whTotal = sheet.createRow(rowIdx++);
            whTotal.createCell(0).setCellValue("원천세 합계");
            whTotal.getCell(0).setCellStyle(totalStyle);
            writeMoneyCell(whTotal, 1, data.getWithholdingTotal(), totalStyle);

            rowIdx += 2; // 빈 행 2개

            // 4. 안내 문구
            Row note1 = sheet.createRow(rowIdx++);
            note1.createCell(0).setCellValue("※ 회사 부담분과 산재보험은 요율 기반 추정값입니다.");

            Row note2 = sheet.createRow(rowIdx++);
            note2.createCell(0).setCellValue("※ 정확한 신고 금액은 4대사회보험 정보연계센터 또는 회계 시스템 기준을 사용해주세요.");

            // 컬럼 너비 고정
            for (int i = 0; i < 4; i++) sheet.setColumnWidth(i, 5500);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("세금 집계 엑셀 생성 실패", e);
        } finally {
            wb.dispose();
            try { wb.close(); } catch (IOException ignore) {}
        }
    }

    // 4대보험 행
    private int writeInsuranceRow(Sheet sheet, int rowIdx, String label,
                                   long employee, long employer, CellStyle moneyStyle) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        writeMoneyCell(row, 1, employee, moneyStyle);
        writeMoneyCell(row, 2, employer, moneyStyle);
        writeMoneyCell(row, 3, employee + employer, moneyStyle);
        return rowIdx + 1;
    }

    private void writeMoneyCell(Row row, int col, long value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    // 큰 제목 스타일
    private CellStyle buildTitleStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 12);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.LEFT);
        return s;
    }

    // 합계 행 스타일 굵게 회색 배경
    private CellStyle buildTotalStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        DataFormat df = wb.createDataFormat();
        s.setDataFormat(df.getFormat("#,##0"));
        return s;
    }

    // 캐시(5분 TTL) 활용
    private Map<UUID, MemberResDto> fetchMemberMap(UUID companyId) {
        return cachedMemberLookup.getMembersByCompany(companyId).stream()
                .collect(Collectors.toMap(MemberResDto::getMemberId, m -> m, (a, b) -> a));
    }

    private List<String> collectItemNames(List<Payroll> payrolls, ItemType type) {
        Map<String, Integer> nameToOrder = new LinkedHashMap<>();
        for (Payroll p : payrolls) {
            for (PayrollItem it : p.getPayrollItemList()) {
                if (!"N".equals(it.getDelYn())) continue;
                if (it.getItemType() != type) continue;
                int order = it.getDisplayOrder() == null ? Integer.MAX_VALUE : it.getDisplayOrder();
                nameToOrder.merge(it.getItemName(), order, Math::min);
            }
        }
        return nameToOrder.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();
    }

    private int writeMoney(Row row, int col, Long value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? 0L : value);
        c.setCellStyle(style);
        return col + 1;
    }

    private String statusKorean(String status) {
        return switch (status) {
            case "DRAFT" -> STATUS_DRAFT_KO;
            case "CONFIRMED" -> STATUS_CONFIRMED_KO;
            case "PAID" -> STATUS_PAID_KO;
            default -> status;
        };
    }

    private CellStyle buildHeaderStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private CellStyle buildMoneyStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        DataFormat df = wb.createDataFormat();
        s.setDataFormat(df.getFormat("#,##0"));
        s.setAlignment(HorizontalAlignment.RIGHT);
        return s;
    }
}