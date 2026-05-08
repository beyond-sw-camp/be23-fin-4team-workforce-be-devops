package com._team._team.company.feignclients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

@FeignClient(
        name = "ai-service",
        contextId = "aiSyncClient",
        url = "${feign.url.ai-service:}"
)
public interface AiSyncClient {

    @PostMapping("/ai/admin/sync/leave/{companyId}")
    void syncLeave(@PathVariable("companyId") UUID companyId);

    @PostMapping("/ai/admin/sync/attendance/{companyId}")
    void syncAttendance(@PathVariable("companyId") UUID companyId);

    @PostMapping("/ai/admin/sync/salary/{companyId}")
    void syncSalary(@PathVariable("companyId") UUID companyId);

    @PostMapping("/ai/admin/sync/approval/{companyId}")
    void syncApproval(@PathVariable("companyId") UUID companyId);
}