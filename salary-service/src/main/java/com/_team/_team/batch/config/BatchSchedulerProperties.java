package com._team._team.batch.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Quartz 스케줄 등록, YAML 에서 제어
 * jobs: 글로벌 1회 등록 (회사 구분 없는 잡 - 주52시간 감지, 만료 처리 등)
 * 회사마다 다른 시각 가능 - 급여, 출퇴근 마감, 연차 부여 등
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "batch.scheduler")
public class BatchSchedulerProperties {

    /**
     * 운영 환경에서는 JDBC JobStore 및 크론을 검증한 뒤 true 설정
     */
    private boolean enabled = false;

    /**
     * 글로벌 잡
     * (회사 구분 없이 1회 등록되는 잡)
     */
    private Map<String, String> jobs = new LinkedHashMap<>();

    /**
     * 회사별 잡
     * (회사 신규 가입 시 이 cron 으로 시드, 회사 관리자가 이후 변경 가능)
     */
    private Map<String, String> perCompanyJobs = new LinkedHashMap<>();
}
