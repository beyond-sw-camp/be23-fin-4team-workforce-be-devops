package com._team._team.saas.tax;

import com._team._team.dto.ApiResponse;
import com._team._team.salary.service.SimplifiedTaxTableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SaaS 운영자용 간이세액표 관리
 * 국세청 고시 표는 회사별이 아니라 전 회사 공통이라 운영자가 매년 1월 업로드
 */
@RestController
@RequestMapping("/saas/tax-table")
public class SaasTaxTableController {

    private final SimplifiedTaxTableService simplifiedTaxTableService;

    @Autowired
    public SaasTaxTableController(SimplifiedTaxTableService simplifiedTaxTableService) {
        this.simplifiedTaxTableService = simplifiedTaxTableService;
    }

    // 엑셀 업로드 같은 연도 행 있으면 소프트 삭제 후 새로 INSERT
    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("effectiveYear") Integer effectiveYear,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "force", required = false, defaultValue = "false") boolean force) {
        int inserted = simplifiedTaxTableService.uploadTaxTable(effectiveYear, file, force);
        Map<String, Object> data = new HashMap<>();
        data.put("effectiveYear", effectiveYear);
        data.put("inserted", inserted);
        return new ResponseEntity<>(
                ApiResponse.success(data, "간이세액표 업로드 성공"),
                HttpStatus.OK);
    }

    // 등록된 연도 목록
    @GetMapping("/years")
    public ResponseEntity<?> years() {
        List<Integer> years = simplifiedTaxTableService.listEffectiveYears();
        return new ResponseEntity<>(
                ApiResponse.success(years, "등록 연도 조회 성공"),
                HttpStatus.OK);
    }

    // 연도별 등록 행 수
    @GetMapping("/count")
    public ResponseEntity<?> count(@RequestParam("year") Integer year) {
        long count = simplifiedTaxTableService.countByYear(year);
        Map<String, Object> data = new HashMap<>();
        data.put("year", year);
        data.put("count", count);
        return new ResponseEntity<>(
                ApiResponse.success(data, "행 수 조회 성공"),
                HttpStatus.OK);
    }
}
