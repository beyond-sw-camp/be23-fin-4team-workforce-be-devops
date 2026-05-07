package com._team._team.salary.feignClients.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * - 데모용으로 회사 도메인으로 조회 시 사용
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyInfoResDto {
    private UUID companyId;
    private String companyName;
    private String companyDomain;
    private String logoUrl;
    private String sealImageUrl;
}
