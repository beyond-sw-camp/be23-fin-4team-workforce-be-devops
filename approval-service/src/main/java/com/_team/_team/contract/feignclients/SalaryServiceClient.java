package com._team._team.contract.feignclients;

import com._team._team.contract.feignclients.dto.SalaryApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "salary-service", contextId = "contractSalaryClient", url = "${feign.url.salary-service:}")
public interface SalaryServiceClient {

    @GetMapping("/salary/salaries/member/{memberId}")
    SalaryApiResponse getSalaryByMemberId(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable("memberId") UUID memberId);
}
