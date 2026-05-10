package com._team._team.attendance.feignClients;

import com._team._team.attendance.dto.resDto.LegalHolidayResDto;
import com._team._team.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "member-service", contextId = "memberCalendarClient")
public interface MemberCalendarClient {

    @GetMapping("/legal-holidays")
    ApiResponse<List<LegalHolidayResDto>> getHolidays(@RequestParam("year") int year);
}