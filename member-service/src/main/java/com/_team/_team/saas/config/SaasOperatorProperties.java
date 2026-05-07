package com._team._team.saas.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * SaaS 운영자 환경 설정
 * system-company-id : 시스템(운영자) 회사 UUID
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "saas")
public class SaasOperatorProperties {

    private String systemCompanyId;
}
