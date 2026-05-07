package com._team._team.company.dto.resdto;

import com._team._team.company.domain.Company;
import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyInfoResDto {

    private UUID companyId;
    private String companyName;
    private String companyDomain;
    private String logoUrl;
    private String sealImageUrl;

    public static CompanyInfoResDto fromEntity(Company company) {
        return CompanyInfoResDto.builder()
                .companyId(company.getCompanyId())
                .companyName(company.getCompanyName())
                .companyDomain(company.getCompanyDomain())
                .logoUrl(company.getLogoUrl())
                .sealImageUrl(company.getSealImageUrl())
                .build();
    }
}