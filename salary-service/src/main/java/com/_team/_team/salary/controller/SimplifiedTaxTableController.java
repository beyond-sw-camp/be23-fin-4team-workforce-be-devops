package com._team._team.salary.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.salary.service.SimplifiedTaxTableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 간이세액표 관리 국세청 고시 표 등록 / 조회
// 매년 1월 새 표 업로드 후 그 해 급여 계산에 자동 반영
@RestController
@RequestMapping("/salary/simplified-tax-table")
public class SimplifiedTaxTableController {

    private final SimplifiedTaxTableService simplifiedTaxTableService;

    @Autowired
    public SimplifiedTaxTableController(SimplifiedTaxTableService simplifiedTaxTableService) {
        this.simplifiedTaxTableService = simplifiedTaxTableService;
    }

    // 엑셀 업로드 multipart 형식
    // 같은 연도 행 있으면 소프트 삭제 후 새로 INSERT 갱신 처리
    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("effectiveYear") Integer effectiveYear,
            @RequestParam("file") MultipartFile file) {
        int inserted = simplifiedTaxTableService.uploadTaxTable(effectiveYear, file);
        Map<String, Object> data = new HashMap<>();
        data.put("effectiveYear", effectiveYear);
        data.put("inserted", inserted);
        return new ResponseEntity<>(
                ApiResponse.success(data, "간이세액표 업로드 성공"),
                HttpStatus.OK);
    }

    // 등록된 연도 목록 화면 표시용
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
