package com._team._team.salary.service;

import com._team._team.dto.BusinessException;
import com._team._team.salary.domain.SimplifiedTaxTable;
import com._team._team.salary.repository.SimplifiedTaxTableRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional
public class SimplifiedTaxTableService {

    // 간이세액표 엑셀 포맷 상수
    private static final int HEADER_ROW_COUNT = 3;   // 상단 헤더 스킵
    private static final int COL_LOWER_BOUND = 0;
    private static final int COL_UPPER_BOUND = 1;
    private static final int COL_DEPENDENT_START = 2;   // 0명부터 시작
    private static final int DEPENDENT_MAX = 11;        // 0~11명

    private final SimplifiedTaxTableRepository repository;

    @Autowired
    public SimplifiedTaxTableService(SimplifiedTaxTableRepository repository) {
        this.repository = repository;
    }

    /**
     * 국세청 간이세액표 엑셀 업로드
     * 기존 연도 데이터가 있으면 소프트 삭제 후 새로 삽입
     */
    public int uploadTaxTable(Integer effectiveYear, MultipartFile file) {
        return uploadTaxTable(effectiveYear, file, false);
    }

    /**
     * 이미 같은 연도 있어도 덮어씀
     */
    public int uploadTaxTable(Integer effectiveYear, MultipartFile file, boolean force) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "파일이 비어있습니다.");
        }

        long existing = repository.countByEffectiveYearAndDelYn(effectiveYear, "N");
        if (existing > 0 && !force) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    effectiveYear + "년 간이세액표가 이미 등록되어 있어요. 덮어쓰려면 다시 시도해주세요.");
        }
        // 이미 등록된 연도 재업로드
        repository.hardDeleteByYear(effectiveYear);

        List<SimplifiedTaxTable> rows = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            // 시트 자동 매칭 산식 표지가 앞에 있고 데이터 표가 뒤에 있는 형식 대응
            Sheet sheet = pickDataSheet(workbook);
            log.info("[TAX-TABLE] 사용 시트 name={}", sheet.getSheetName());
            int lastRow = sheet.getLastRowNum();

            // 헤더 자동 감지 첫 셀이 숫자인 행 찾기
            int firstDataRow = -1;
            for (int r = 0; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Long maybe = readLong(row.getCell(COL_LOWER_BOUND));
                if (maybe != null && maybe > 0) {
                    firstDataRow = r;
                    break;
                }
            }
            if (firstDataRow < 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "데이터 행을 찾을 수 없습니다. 엑셀 형식 확인 필요");
            }
            log.info("[TAX-TABLE] 데이터 시작 행 firstDataRow={}", firstDataRow);

            // 단위 감지 첫 데이터 행의 lower 값이 1만 미만이면 천원 단위
            Row firstRow = sheet.getRow(firstDataRow);
            Long firstLower = readLong(firstRow.getCell(COL_LOWER_BOUND));
            boolean isThousandUnit = firstLower != null && firstLower < 10_000;
            long unitMultiplier = isThousandUnit ? 1_000L : 1L;
            log.info("[TAX-TABLE] 단위 감지 천원 여부={} firstLower={}", isThousandUnit, firstLower);

            // 데이터 행 순회
            int processed = 0;
            int skipped = 0;
            for (int r = firstDataRow; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) { skipped++; continue; }

                Long lower = readLong(row.getCell(COL_LOWER_BOUND));
                Long upper = readLong(row.getCell(COL_UPPER_BOUND));
                if (lower == null || upper == null) { skipped++; continue; }

                long realLower = lower * unitMultiplier;
                long realUpper = upper * unitMultiplier;

                // 부양가족 1 ~ 11 명 컬럼 12개 읽기
                // 우리 dependentCount 매핑 1 = 본인 만 = 홈택스 컬럼 C
                for (int d = 1; d <= DEPENDENT_MAX; d++) {
                    int colIdx = COL_DEPENDENT_START + (d - 1);
                    Long tax = readLong(row.getCell(colIdx));
                    if (tax == null) continue;

                    rows.add(SimplifiedTaxTable.builder()
                            .effectiveYear(effectiveYear)
                            .salaryLowerBound(realLower)
                            .salaryUpperBound(realUpper)
                            .dependentCount(d)
                            .monthlyTaxAmount(tax)
                            .build());
                }
                processed++;
            }

            log.info("[TAX-TABLE] 파싱 완료 처리={} 건너뜀={} INSERT={}",
                    processed, skipped, rows.size());

            repository.saveAll(rows);
            return rows.size();

        } catch (IOException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "엑셀 파일 읽기 실패, " + e.getMessage());
        }
    }

    // 시트 자동 매칭 이름에 간이세액표 조견표 포함된 시트 우선 fallback 마지막 시트
    private Sheet pickDataSheet(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet s = workbook.getSheetAt(i);
            String name = s.getSheetName();
            if (name == null) continue;
            if (name.contains("간이세액표") || name.contains("조견표")) return s;
        }
        // fallback 마지막 시트 일반적으로 산식 표지가 앞 데이터 표가 뒤
        return workbook.getSheetAt(workbook.getNumberOfSheets() - 1);
    }

    // 셀에서 Long 안전 추출, 빈 셀이거나 숫자 아니면 null
    private Long readLong(Cell cell) {
        if (cell == null) return null;
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> (long) cell.getNumericCellValue();
                case STRING -> {
                    String s = cell.getStringCellValue().replaceAll("[,\\s]", "");
                    yield s.isBlank() ? null : Long.parseLong(s);
                }
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // 급여 계산 시 호출, 간이세액표 조회 (자녀세액공제 미반영)
    @Transactional(readOnly = true)
    public Long findTax(int year, long monthlyTaxableSalary, int dependents) {
        return repository.findTaxFor(year, monthlyTaxableSalary, dependents)
                .map(SimplifiedTaxTable::getMonthlyTaxAmount)
                .orElse(0L);
    }

    /**
     * 간이세액표 lookup 후 8~20세 자녀세액공제까지 차감한 월 간이세액
     *
     * 한국 간이세액표는 부양가족수만 차원으로 갖고, 자녀세액공제는 별도로 차감
     * 매월 원천징수 기준이므로 연말정산과는 차이가 있을 수 있음 (단순 월 환산)
     */
    @Transactional(readOnly = true)
    public Long findTaxWithChildDeduction(int year,
                                          long monthlyTaxableSalary,
                                          int dependents,
                                          int childUnder20Count) {
        long base = findTax(year, monthlyTaxableSalary, dependents);
        long childCredit = monthlyChildTaxCredit(childUnder20Count);
        return Math.max(0L, base - childCredit);
    }

    /**
     * 8세 이상 20세 이하 자녀세액공제 월 환산액
     * 연 기준 - 1명 150,000원 / 2명 350,000원 / 3명부터는 1명당 추가 300,000원
     * 매월 간이세액에서 1/12 만큼 차감
     */
    public long monthlyChildTaxCredit(int childUnder20Count) {
        if (childUnder20Count <= 0) return 0L;
        if (childUnder20Count == 1) return 12_500L;
        if (childUnder20Count == 2) return 29_166L;
        return 29_166L + (childUnder20Count - 2) * 25_000L;
    }

    // 등록된 연도 목록 조회 화면 표시용
    @Transactional(readOnly = true)
    public List<Integer> listEffectiveYears() {
        return repository.findDistinctYears();
    }

    // 연도별 등록 행 수
    @Transactional(readOnly = true)
    public long countByYear(int effectiveYear) {
        return repository.countByEffectiveYearAndDelYn(effectiveYear, "N");
    }

    /** 연도별 전체 행 조회 - 화면 테이블 표시용 */
    @Transactional(readOnly = true)
    public List<SimplifiedTaxTable> listByYear(int effectiveYear) {
        return repository.findByEffectiveYearAndDelYnOrderBySalaryLowerBoundAscDependentCountAsc(
                effectiveYear, "N");
    }

    /**
     * 연도별 엑셀 다운로드 - (DB row 를 국세청 양식 비슷하게 재구성해 .xlsx 바이너리 반환)
     * (월급여 구간 1행, 부양가족 0~11명 컬럼)
     */
    @Transactional(readOnly = true)
    public byte[] generateExcel(int effectiveYear) {
        List<SimplifiedTaxTable> rows = repository
                .findByEffectiveYearAndDelYnOrderBySalaryLowerBoundAscDependentCountAsc(effectiveYear, "N");
        if (rows.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, effectiveYear + "년 간이세액표 데이터가 없습니다.");
        }
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(effectiveYear + " 간이세액표");
            // 헤더 - 월급여 하한 / 상한 / 0명 / 1명 / ... / 11명
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("월급여 하한(원)");
            header.createCell(1).setCellValue("월급여 상한(원)");
            for (int i = 0; i <= DEPENDENT_MAX; i++) {
                header.createCell(2 + i).setCellValue(i + "명");
            }
            // (lower, upper) 그룹별로 부양가족 0~11 행 펼치기
            int rowIdx = 1;
            Long curLower = null;
            Long curUpper = null;
            Row curRow = null;
            for (SimplifiedTaxTable t : rows) {
                if (curLower == null
                        || !curLower.equals(t.getSalaryLowerBound())
                        || !curUpper.equals(t.getSalaryUpperBound())) {
                    curRow = sheet.createRow(rowIdx++);
                    curRow.createCell(0).setCellValue(t.getSalaryLowerBound());
                    curRow.createCell(1).setCellValue(t.getSalaryUpperBound());
                    curLower = t.getSalaryLowerBound();
                    curUpper = t.getSalaryUpperBound();
                }
                int dep = t.getDependentCount();
                if (dep >= 0 && dep <= DEPENDENT_MAX) {
                    curRow.createCell(2 + dep).setCellValue(t.getMonthlyTaxAmount());
                }
            }
            // 컬럼 폭 자동
            for (int c = 0; c < 2 + DEPENDENT_MAX + 1; c++) {
                sheet.autoSizeColumn(c);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "엑셀 생성 실패: " + e.getMessage());
        }
    }
}