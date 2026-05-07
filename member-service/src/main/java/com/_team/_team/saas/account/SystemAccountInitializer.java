package com._team._team.saas.account;

import com._team._team.company.domain.Company;
import com._team._team.company.domain.enums.CompanyStatus;
import com._team._team.company.repository.CompanyRepository;
import com._team._team.member.domain.Member;
import com._team._team.member.domain.enums.AccountStatus;
import com._team._team.member.domain.enums.EmploymentType;
import com._team._team.member.domain.enums.MemberStatus;
import com._team._team.member.repository.MemberRepository;
import com._team._team.saas.config.SaasOperatorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부팅 시 SaaS 운영자 계정 초기화
 */
@Slf4j
@Component
public class SystemAccountInitializer implements ApplicationRunner {

    // default 운영자 정보
    private static final String DEFAULT_OPERATOR_EMAIL = "saas@saas.com";
    private static final String DEFAULT_OPERATOR_NAME = "SaaS 운영자";
    /** BCrypt 해시 - 평문: test1234! */
    private static final String DEFAULT_OPERATOR_PASSWORD_HASH =
            "{bcrypt}$2a$10$jTk0Tfvn/l3Tkmd9ySD02ulLQzuARw7WgRTAL.UhnB7u8H5Az9JHu";

    private static final String SYS_COMPANY_NAME = "SaaS Operations";
    private static final String SYS_COMPANY_BUSINESS_NUMBER = "000-00-00000";
    private static final String SYS_COMPANY_DOMAIN = "saas-internal";

    final SaasOperatorProperties properties;
    final CompanyRepository companyRepository;
    final MemberRepository memberRepository;

    @Autowired
    public SystemAccountInitializer(SaasOperatorProperties properties,
                                    CompanyRepository companyRepository,
                                    MemberRepository memberRepository) {
        this.properties = properties;
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 시스템 회사
        Company sysCompany = companyRepository.findByBusinessNumber(SYS_COMPANY_BUSINESS_NUMBER)
                .orElseGet(() -> {
                    Company created = Company.builder()
                            .companyName(SYS_COMPANY_NAME)
                            .companyDomain(SYS_COMPANY_DOMAIN)
                            .ceoName("system")
                            .businessNumber(SYS_COMPANY_BUSINESS_NUMBER)
                            .address("-")
                            .detailAddress("-")
                            .status(CompanyStatus.ACTIVE)
                            .build();
                    Company saved = companyRepository.save(created);
                    log.info("[SAAS-INIT] 시스템 회사 생성 companyId={}", saved.getCompanyId());
                    return saved;
                });

        // 운영자 식별용 system-company-id 메모리 캐싱
        properties.setSystemCompanyId(sysCompany.getCompanyId().toString());

        if (memberRepository.findByEmail(DEFAULT_OPERATOR_EMAIL).isEmpty()) {
            Member operator = Member.builder()
                    .company(sysCompany)
                    .email(DEFAULT_OPERATOR_EMAIL)
                    .personalEmail(DEFAULT_OPERATOR_EMAIL)
                    .password(DEFAULT_OPERATOR_PASSWORD_HASH)
                    .name(DEFAULT_OPERATOR_NAME)
                    .memberStatus(MemberStatus.ACTIVE)
                    .accountStatus(AccountStatus.ACTIVE)
                    .employmentType(EmploymentType.FULL_TIME)
                    .build();
            memberRepository.save(operator);
            log.info("[SAAS-INIT] 운영자 멤버 생성 email={}", DEFAULT_OPERATOR_EMAIL);
        }
    }
}
