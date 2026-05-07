package com._team._team.batch;

import com._team._team.batch.config.BatchSchedulerProperties;
import com._team._team.salary.repository.SalaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

/**
 * salary-service 배치용 Quartz
 * 글로벌 잡: 잡 1개당 트리거 1개
 * 회사별 잡: 회사 × 잡 = N×M 트리거 JobDataMap 에 companyId 저장
 * 회사 식별자: jobName 에 companyId 포함
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(BatchSchedulerProperties.class)
public class BatchScheduler {

    private static final String QUARTZ_GROUP = "BATCH_GROUP";
    /** jobName 과 companyId 를 합칠 때 쓰는 구분자 - 잡 이름엔 안 나오는 문자 사용 */
    public static final String COMPANY_DELIMITER = "__";

    private final Scheduler scheduler;
    private final BatchSchedulerProperties schedulerProperties;
    private final SalaryRepository salaryRepository;

    @Autowired
    public BatchScheduler(Scheduler scheduler,
                          BatchSchedulerProperties schedulerProperties,
                          SalaryRepository salaryRepository) {
        this.scheduler = scheduler;
        this.schedulerProperties = schedulerProperties;
        this.salaryRepository = salaryRepository;
    }

    @PostConstruct
    public void scheduleJobs() {
        log.info("[BATCH-SCHED] PostConstruct enabled={} globalJobs={} perCompanyJobs={}",
                schedulerProperties.isEnabled(),
                schedulerProperties.getJobs() == null ? -1 : schedulerProperties.getJobs().size(),
                schedulerProperties.getPerCompanyJobs() == null ? -1 : schedulerProperties.getPerCompanyJobs().size());
        try {
            if (!schedulerProperties.isEnabled()) {
                log.info("Quartz batch scheduler disabled. No triggers registered.");
                return;
            }

            // 1) 글로벌 잡 등록
            Map<String, String> globalJobs = schedulerProperties.getJobs();
            if (globalJobs != null) {
                for (var e : globalJobs.entrySet()) {
                    registerGlobalJob(e.getKey(), e.getValue());
                }
            }

            // 2) 회사별 잡 - 부팅 시점 기준 salary 가 있는 모든 회사에 잡 N개씩 시드
            Map<String, String> perCompanyJobs = schedulerProperties.getPerCompanyJobs();
            if (perCompanyJobs != null && !perCompanyJobs.isEmpty()) {
                List<UUID> companyIds = salaryRepository.findDistinctCompanyIds();
                log.info("[BATCH-SCHED] 회사별 잡 시드 대상 회사 {} 개", companyIds.size());
                for (UUID companyId : companyIds) {
                    for (var e : perCompanyJobs.entrySet()) {
                        registerPerCompanyJob(e.getKey(), e.getValue(), companyId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to register Quartz jobs", e);
        }
    }

    /** 글로벌 잡 - 회사 구분 없이 트리거 1개 */
    private void registerGlobalJob(String jobName, String cronExpression) throws SchedulerException {
        TriggerKey triggerKey = TriggerKey.triggerKey(jobName + "_Trigger", QUARTZ_GROUP);
        if (scheduler.checkExists(triggerKey)) {
            log.info("[Batch][Quartz] 글로벌 트리거 유지 jobName={}", jobName);
            return;
        }

        JobDetail jobDetail = JobBuilder.newJob(BatchScheduledJob.class)
                .withIdentity(jobName + "_Detail", QUARTZ_GROUP)
                .usingJobData("jobName", jobName)
                .storeDurably()
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobDetail)
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression)
                        .inTimeZone(TimeZone.getTimeZone("Asia/Seoul")))
                .build();
        scheduler.scheduleJob(jobDetail, trigger);
        log.info("[Batch][Quartz] 글로벌 등록 jobName={} cron={}", jobName, cronExpression);
    }

    /**
     * 회사별 잡
     */
    public void registerPerCompanyJob(String jobName, String cronExpression, UUID companyId) throws SchedulerException {
        String fullName = jobName + COMPANY_DELIMITER + companyId;
        TriggerKey triggerKey = TriggerKey.triggerKey(fullName + "_Trigger", QUARTZ_GROUP);
        if (scheduler.checkExists(triggerKey)) {
            return;
        }

        JobDetail jobDetail = JobBuilder.newJob(BatchScheduledJob.class)
                .withIdentity(fullName + "_Detail", QUARTZ_GROUP)
                .usingJobData("jobName", jobName)
                .usingJobData("companyId", companyId.toString())
                .storeDurably()
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobDetail)
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression)
                        .inTimeZone(TimeZone.getTimeZone("Asia/Seoul")))
                .build();
        scheduler.scheduleJob(jobDetail, trigger);
        log.info("[Batch][Quartz] 회사별 등록 jobName={} companyId={} cron={}", jobName, companyId, cronExpression);
    }

    /** 회사 비활성화/탈퇴 시 호출 - 그 회사의 모든 회사별 잡 트리거 제거 */
    public void unscheduleAllForCompany(UUID companyId) throws SchedulerException {
        Map<String, String> perCompanyJobs = schedulerProperties.getPerCompanyJobs();
        if (perCompanyJobs == null) return;
        for (String jobName : perCompanyJobs.keySet()) {
            String fullName = jobName + COMPANY_DELIMITER + companyId;
            JobKey jobKey = JobKey.jobKey(fullName + "_Detail", QUARTZ_GROUP);
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
                log.info("[Batch][Quartz] 회사별 트리거 제거 jobName={} companyId={}", jobName, companyId);
            }
        }
    }
}
