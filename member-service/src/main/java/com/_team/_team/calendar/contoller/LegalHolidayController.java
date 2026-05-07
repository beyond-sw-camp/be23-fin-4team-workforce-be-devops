package com._team._team.calendar.contoller;

import com._team._team.dto.ApiResponse;
import com._team._team.calendar.service.LegalHolidayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/legal-holidays")
@RequiredArgsConstructor
public class LegalHolidayController {

    private final LegalHolidayService legalHolidayService;

    // 공휴일 조회
    @GetMapping
    public ResponseEntity<?> getHolidays(@RequestParam int year) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        legalHolidayService.getHolidays(year),
                        "공휴일 조회 성공"),
                HttpStatus.OK
        );
    }

    // 수동 수집 (관리자용)
    @PostMapping("/collect")
    public ResponseEntity<?> collectHolidays(@RequestParam int year) {
        legalHolidayService.collectAndSaveHolidays(year);
        return new ResponseEntity<>(
                ApiResponse.success(null, "공휴일 수집 완료"),
                HttpStatus.OK
        );
    }
}