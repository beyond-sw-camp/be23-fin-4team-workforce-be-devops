package com._team._team.saas.company;

import com._team._team.company.domain.Company;
import com._team._team.company.repository.CompanyRepository;
import com._team._team.dto.ApiResponse;
import com._team._team.saas.config.SaasOperatorProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// SaaS 운영자용 회사 목록 조회
@RestController
@RequestMapping("/saas/companies")
public class SaasCompanyController {

    private final CompanyRepository companyRepository;
    private final SaasOperatorProperties operatorProperties;

    @Autowired
    public SaasCompanyController(CompanyRepository companyRepository,
                                 SaasOperatorProperties operatorProperties) {
        this.companyRepository = companyRepository;
        this.operatorProperties = operatorProperties;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        String systemCompanyId = operatorProperties.getSystemCompanyId();
        List<Map<String, String>> result = companyRepository.findAll().stream()
                .filter(c -> c.getCompanyId() != null)
                .filter(c -> systemCompanyId == null || !systemCompanyId.equals(c.getCompanyId().toString()))
                .map(SaasCompanyController::toBrief)
                .toList();
        return new ResponseEntity<>(ApiResponse.success(result, "회사 목록 조회 성공"), HttpStatus.OK);
    }

    private static Map<String, String> toBrief(Company c) {
        return Map.of(
                "companyId", c.getCompanyId().toString(),
                "companyName", c.getCompanyName() == null ? "" : c.getCompanyName(),
                "businessNumber", c.getBusinessNumber() == null ? "" : c.getBusinessNumber()
        );
    }
}
