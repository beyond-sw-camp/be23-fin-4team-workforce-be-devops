package com._team._team.salary.controller;

import com._team._team.batch.payroll.worker.PayrollCalculateWorker;
import com._team._team.dto.ApiResponse;
import com._team._team.salary.service.PayrollService;
import com._team._team.salary.service.PayslipPdfService;
import com._team._team.salary.service.TaxSummaryService;
import com._team._team.salary.dto.reqdto.PayrollCreateReqDto;
import com._team._team.salary.dto.reqdto.PayrollItemCreateReqDto;
import com._team._team.salary.dto.reqdto.PayrollItemUpdateReqDto;
import com._team._team.salary.dto.reqdto.PayrollRecalculateReqDto;
import com._team._team.salary.dto.reqdto.PayrollUpdateReqDto;
import com._team._team.salary.dto.resdto.BulkPayrollActionResDto;
import com._team._team.salary.dto.resdto.MyAnnualSalaryResDto;
import com._team._team.salary.dto.resdto.PayrollAdminListResDto;
import com._team._team.salary.dto.resdto.PayrollRecalculateResDto;
import com._team._team.salary.dto.resdto.TaxSummaryResDto;
import java.time.YearMonth;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com._team._team.salary.domain.enums.TaxCategory;
import com._team._team.salary.service.PayrollExportService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/salary/payroll")
public class PayrollController {

    private final PayrollService payrollService;
    private final PayrollCalculateWorker payrollCalculateWorker;
    private final PayslipPdfService payslipPdfService;
    private final TaxSummaryService taxSummaryService;
    private final PayrollExportService payrollExportService;

    @Autowired
    public PayrollController(PayrollService payrollService,
                             PayrollCalculateWorker payrollCalculateWorker,
                             PayslipPdfService payslipPdfService,
                             TaxSummaryService taxSummaryService, PayrollExportService payrollExportService) {
        this.payrollService = payrollService;
        this.payrollCalculateWorker = payrollCalculateWorker;
        this.payslipPdfService = payslipPdfService;
        this.taxSummaryService = taxSummaryService;
        this.payrollExportService = payrollExportService;
    }

    // 직원 본인 연봉 조회 연도 합계 + 월별 + 항목별 누적
    @GetMapping("/my/annual")
    public ResponseEntity<?> findMyAnnual(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestParam(value = "year", required = false) Integer year) {

        int targetYear = (year == null) ? java.time.Year.now().getValue() : year;
        MyAnnualSalaryResDto data = payrollService.findMyAnnual(companyId, memberId, targetYear);
        return new ResponseEntity<>(
                ApiResponse.success(data, "연봉 조회 성공"),
                HttpStatus.OK);
    }

    // 4대보험 + 원천세 월별 집계 한 번 호출로 두 섹션 채움
    @GetMapping("/tax-summary")
    public ResponseEntity<?> getTaxSummary(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestParam(value = "yearMonth", required = false) String yearMonthStr) {
        YearMonth yearMonth = (yearMonthStr == null || yearMonthStr.isBlank())
                ? YearMonth.now()
                : YearMonth.parse(yearMonthStr);
        TaxSummaryResDto data = taxSummaryService.getMonthlySummary(companyId, yearMonth);
        return new ResponseEntity<>(
                ApiResponse.success(data, "세금 집계 조회 성공"),
                HttpStatus.OK);
    }

    // 회사 월 단위 급여대장 전체 조회 메인 화면용 직원 정보 결합
    @GetMapping("/admin/list")
    public ResponseEntity<?> listByCompanyMonth(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestParam(value = "yearMonth", required = false) String yearMonthStr) {

        YearMonth yearMonth = (yearMonthStr == null || yearMonthStr.isBlank())
                ? YearMonth.now()
                : YearMonth.parse(yearMonthStr);

        List<PayrollAdminListResDto> data = payrollService.listByCompanyAndMonth(companyId, yearMonth);
        return new ResponseEntity<>(
                ApiResponse.success(data, "회사 월 단위 급여대장 조회 성공"),
                HttpStatus.OK);
    }

    // 일괄 확정 다중 선택 payrollIds 한 번에 처리
    @PostMapping("/bulk-confirm")
    public ResponseEntity<?> bulkConfirm(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestBody Map<String, List<UUID>> body) {

        List<UUID> payrollIds = body.getOrDefault("payrollIds", List.of());
        BulkPayrollActionResDto data = payrollService.bulkConfirm(companyId, payrollIds);
        return new ResponseEntity<>(
                ApiResponse.success(data, "일괄 확정 처리 완료"),
                HttpStatus.OK);
    }

    // 일괄 지급 처리
    @PostMapping("/bulk-pay")
    public ResponseEntity<?> bulkPay(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestBody Map<String, List<UUID>> body) {

        List<UUID> payrollIds = body.getOrDefault("payrollIds", List.of());
        BulkPayrollActionResDto data = payrollService.bulkPay(companyId, payrollIds);
        return new ResponseEntity<>(
                ApiResponse.success(data, "일괄 지급 처리 완료"),
                HttpStatus.OK);
    }

    // 세금 4대보험 월별 집계 엑셀 다운로드 신고용 자료
    @GetMapping("/tax-summary/export")
    public ResponseEntity<byte[]> exportTaxSummary(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestParam(value = "yearMonth", required = false) String yearMonthStr) {

        YearMonth yearMonth = (yearMonthStr == null || yearMonthStr.isBlank())
                ? YearMonth.now()
                : YearMonth.parse(yearMonthStr);

        byte[] data = payrollExportService.exportTaxSummaryXlsx(companyId, yearMonth);

        String filename = "tax-summary_" + yearMonth + ".xlsx";
        String encoded = java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encoded);
        headers.setContentType(
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    // 회사 월 단위 급여대장 엑셀 다운로드
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportMonthlyPayroll(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestParam(value = "yearMonth", required = false) String yearMonthStr) {

        YearMonth yearMonth = (yearMonthStr == null || yearMonthStr.isBlank())
                ? YearMonth.now()
                : YearMonth.parse(yearMonthStr);

        byte[] data = payrollExportService.exportMonthlyXlsx(companyId, yearMonth);

        String filename = "payroll_" + yearMonth + ".xlsx";
        String encoded = java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encoded);
        headers.setContentType(
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    // ===================== 급여대장 =====================

    /** 급여대장 생성 */
    @PostMapping("/create")
    public ResponseEntity<?> createPayroll(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody PayrollCreateReqDto reqDto){
        return new ResponseEntity<>(
                ApiResponse.success(payrollService.createPayroll(companyId, reqDto), "급여대장이 생성되었습니다."),
                HttpStatus.CREATED
        );
    }

    /** 급여대장 단건 조회 */
    @GetMapping("/{payrollId}")
    public ResponseEntity<?> findPayrollById(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID payrollId){
        return new ResponseEntity<>(
                ApiResponse.success(payrollService.findPayrollById(companyId, payrollId), "급여대장 조회 성공"),
                HttpStatus.OK
        );
    }

    /** 급여명세서 PDF 다운로드 (본인 또는 급여 조회 권한) */
    @GetMapping("/{payrollId}/payslip.pdf")
    public ResponseEntity<?> downloadPayslipPdf(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID requesterMemberId,
            @RequestHeader(value = "X-User-MemberPositionId", required = false) UUID requesterPositionId,
            @RequestHeader(value = "X-User-IsSystemAdmin", required = false) String isSystemAdmin,
            @PathVariable UUID payrollId) {

        byte[] pdf = payslipPdfService.buildPdf(
                companyId, payrollId, requesterMemberId, requesterPositionId, isSystemAdmin);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentLength(pdf.length);
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename("payslip-" + payrollId + ".pdf", StandardCharsets.UTF_8)
                        .build());

        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    /** 직원별 급여대장 목록 조회 */
    @GetMapping("/member/{memberId}")
    public ResponseEntity<?> findPayrollsByMemberId(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID memberId){
        return new ResponseEntity<>(
                ApiResponse.success(payrollService.findPayrollByMemberId(companyId, memberId), "직원별 급여대장 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    /** 급여대장 수정 */
    @PutMapping("/{payrollId}")
    public ResponseEntity<?> updatePayroll(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID payrollId,
            @Valid @RequestBody PayrollUpdateReqDto reqDto){
        return new ResponseEntity<>(
                ApiResponse.success(payrollService.updatePayroll(companyId, payrollId, reqDto), "급여대장이 수정되었습니다."),
                HttpStatus.OK
        );
    }

    /** 급여대장 삭제 */
    @DeleteMapping("/{payrollId}")
    public ResponseEntity<?> deletePayroll(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID payrollId){
        payrollService.deletePayroll(companyId, payrollId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "급여대장이 삭제되었습니다."),
                HttpStatus.OK
        );
    }

    /** 급여 확정 (DRAFT → CONFIRMED) */
    @PatchMapping("/{payrollId}/confirm")
    public ResponseEntity<?> confirmPayroll(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID payrollId) {
        return new ResponseEntity<>(
                ApiResponse.success(payrollService.confirmPayroll(companyId, payrollId), "급여대장이 확정되었습니다."),
                HttpStatus.OK
        );
    }

    /** 지급 완료 (CONFIRMED → PAID) */
    @PatchMapping("/{payrollId}/pay")
    public ResponseEntity<?> payPayroll(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID payrollId) {
        return new ResponseEntity<>(
                ApiResponse.success(payrollService.payPayroll(companyId, payrollId), "급여가 지급 완료되었습니다."),
                HttpStatus.OK
        );
    }

    // 회사 단위 급여대장 재계산
    // 자동 배치 누락 / 마스터 데이터 늦게 등록한 경우 관리자가 직접 호출
    @PostMapping("/recalculate")
    public ResponseEntity<?> recalculate(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody PayrollRecalculateReqDto reqDto) {
        PayrollCalculateWorker.Counter c =
                payrollCalculateWorker.runForCompany(companyId, reqDto.getSettlementDate());
        return new ResponseEntity<>(
                ApiResponse.success(PayrollRecalculateResDto.from(c), "급여대장 재계산이 완료되었습니다."),
                HttpStatus.OK
        );
    }

    // ===================== 급여대장 항목 =====================

    /** 급여대장 항목 추가 */
    @PostMapping("/{payrollId}/items")
    public ResponseEntity<?> createPayrollItem(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID payrollId,
            @Valid @RequestBody PayrollItemCreateReqDto reqDto) {
        return new ResponseEntity<>(
                ApiResponse.success(payrollService.createPayrollItem(companyId, payrollId, reqDto), "급여대장 항목이 추가되었습니다."),
                HttpStatus.CREATED
        );
    }

    /** 급여대장별 항목 목록 조회 */
    @GetMapping("/{payrollId}/items")
    public ResponseEntity<?> findPayrollItems(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID payrollId) {
        return new ResponseEntity<>(
                ApiResponse.success(payrollService.findPayrollItems(companyId, payrollId), "급여대장 항목 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    /** 급여대장 항목 수정 */
    @PutMapping("/items/{payrollItemId}")
    public ResponseEntity<?> updatePayrollItem(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID payrollItemId,
            @Valid @RequestBody PayrollItemUpdateReqDto reqDto) {
        return new ResponseEntity<>(
                ApiResponse.success(payrollService.updatePayrollItem(companyId, payrollItemId, reqDto), "급여대장 항목이 수정되었습니다."),
                HttpStatus.OK
        );
    }

    /** 급여대장 항목 삭제 (소프트 삭제) */
    @DeleteMapping("/items/{payrollItemId}")
    public ResponseEntity<?> deletePayrollItem(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID payrollItemId) {
        payrollService.deletePayrollItem(companyId, payrollItemId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "급여대장 항목이 삭제되었습니다."),
                HttpStatus.OK
        );
    }
}
