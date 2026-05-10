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

    /**
     * 인사발령 적용 시 활성 Salary 의 직급/직책 동기화
     * - 호봉제 회사라면 step 도 1로 reset (회사 admin 이 별도 조정 가능)
     * - 호봉/기본급 자동 lookup 은 v2 (현재는 직급/직책 라벨만 갱신)
     */
    @PostMapping("/salary/internal/apply-personnel-order")
    void applyPersonnelOrder(
            @RequestParam("memberId") UUID memberId,
            @RequestParam("companyId") UUID companyId,
            @RequestParam(value = "newJobGradeName", required = false) String newJobGradeName,
            @RequestParam(value = "newJobTitleName", required = false) String newJobTitleName,
            @RequestParam(value = "newStep", required = false) Integer newStep);
}