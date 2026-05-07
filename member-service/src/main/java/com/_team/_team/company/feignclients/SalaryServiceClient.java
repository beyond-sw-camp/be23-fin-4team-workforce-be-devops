package com._team._team.company.feignclients;

import com._team._team.calendar.dto.resdto.CompanyHolidayFeignDto;
import com._team._team.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "salary-service")
public interface SalaryServiceClient {

    // 회사 생성 시 기본 휴가 종류 8종 추가
    @PostMapping("/attendance/leave-types/init")
    void initDefaultLeaveTypes(@RequestParam("companyId") UUID companyId);

    // 법정 공휴일 복사
    @PostMapping("/company-holidays/import-public")
    void importPublicHolidays(@RequestParam("companyId") UUID companyId);

    // 회사별 자동 작업 트리거 일괄 시드, 회사 신규 가입 시 호출
    @PostMapping("/salary/internal/batch-schedule/init")
    void initBatchSchedule(@RequestParam("companyId") UUID companyId);

    // 회사 휴일 전체 목록 (법정+커스텀 통합)
    @GetMapping("/company-holidays/internal")
    ApiResponse<List<CompanyHolidayFeignDto>> findCompanyHolidays(@RequestParam("companyId") UUID companyId);
}