package com._team._team.company.feignclients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "approval-service")
public interface ApprovalServiceClient {

    @PostMapping("/approval/documents/init")
    void initDefaultDocuments(@RequestParam("companyId") UUID companyId);

    @PostMapping("/contract/templates/init")
    void initDefaultContractTemplates(@RequestParam("companyId") UUID companyId);
}
