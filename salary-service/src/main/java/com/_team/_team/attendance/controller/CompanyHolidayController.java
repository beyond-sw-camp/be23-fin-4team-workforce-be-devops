package com._team._team.attendance.controller;

import com._team._team.attendance.service.CompanyHolidayService;
import com._team._team.attendance.dto.reqDto.CompanyHolidayCreateReqDto;
import com._team._team.attendance.dto.reqDto.CompanyHolidayUpdateReqDto;
import com._team._team.attendance.dto.resDto.CompanyHolidayResDto;
import com._team._team.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/company-holidays")
public class CompanyHolidayController {
    private final CompanyHolidayService companyHolidayService;

    @Autowired
    public CompanyHolidayController(CompanyHolidayService companyHolidayService) {
        this.companyHolidayService = companyHolidayService;
    }

    /** 공휴일 등록 */
    @PostMapping("/create")
    public ResponseEntity<?> createHoliday(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody CompanyHolidayCreateReqDto reqDto) {
        CompanyHolidayResDto resDto = companyHolidayService.createHoliday(companyId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "공휴일이 등록되었습니다."),
                HttpStatus.CREATED
        );
    }

    /** 공휴일 전체 목록 조회 */
    @GetMapping
    public ResponseEntity<?> findHolidays(
            @RequestHeader("X-User-CompanyId") UUID companyId){
        List<CompanyHolidayResDto> resDtoList = companyHolidayService.findHolidays(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(resDtoList, "공휴일 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    /** 공휴일 수정 */
    @PutMapping("/{holidayId}")
    public ResponseEntity<?> updateHoliday(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID holidayId,
            @Valid @RequestBody CompanyHolidayUpdateReqDto reqDto){
        CompanyHolidayResDto resDto = companyHolidayService.updateHoliday(companyId, holidayId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "공휴일이 수정되었습니다."),
                HttpStatus.OK
        );
    }

    /** 공휴일 삭제 */
    @DeleteMapping("/{holidayId}")
    public ResponseEntity<?> deleteHoliday(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID holidayId){
        companyHolidayService.deleteHoliday(companyId, holidayId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "공휴일이 삭제되었습니다."),
                HttpStatus.OK
        );
    }

    // 회사 생성 시 법정 공휴일 일괄 복사
    @PostMapping("/import-public")
    public void importPublicHolidays(@RequestParam UUID companyId) {
        companyHolidayService.importPublicHolidays(companyId);
    }

    /**
     * 관리자 수동 새로고침 UI 용, 지정 연도 법정 공휴일 재수집, 커스텀 휴일은 보존
     */
    @PostMapping("/refresh-legal")
    public ResponseEntity<?> refreshLegalHolidays(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestParam int year) {

        int saved = companyHolidayService.refreshLegalHolidays(companyId, year);

        return new ResponseEntity<>(
                ApiResponse.success(
                        Map.of("year", year, "importedCount", saved),
                        year + "년 법정 공휴일 " + saved + "건 반영 완료"),
                HttpStatus.OK);
    }
    // 내부통신용
    @GetMapping("/internal")
    public ResponseEntity<?> findHolidaysInternal(@RequestParam UUID companyId) {
        List<CompanyHolidayResDto> resDtoList = companyHolidayService.findHolidays(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(resDtoList, "공휴일 목록 조회 성공 (내부)"),
                HttpStatus.OK);
    }
}
