package com._team._team.batch;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.configuration.JobLocator;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Quartz와 Spring Batch를 연결하는 공통 구현체
 */
@Slf4j
@Component
public class BatchScheduledJob extends QuartzJobBean {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YEAR_MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final JobLocator jobLocator;
    private final JobLauncher jobLauncher;

    @Autowired
    public BatchScheduledJob(JobLocator jobLocator, JobLauncher jobLauncher) {
        this.jobLocator = jobLocator;
        this.jobLauncher = jobLauncher;
    }

    /**
     * Quartz 트리거 시 호출
     * Quartz 실행 컨텍스트(jobName은 JobDetail 등록 시 설정)
     */
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        String jobName = context.getMergedJobDataMap().getString("jobName");
        // 회사별 잡이면 companyId 도 함께 전달, 글로벌 잡이면 null
        String companyId = context.getMergedJobDataMap().getString("companyId");
        log.info("Quartz Triggered! Starting Spring Batch Job: {} companyId={}", jobName, companyId);

        try {
            Job job = jobLocator.getJob(jobName);

            // BATCH_JOB_INSTANCE 추적성 확보용으로 의미있는 비즈니스 키 추가
            // time 은 매번 다른 인스턴스 보장, yearMonth/date 는 운영 가시성용
            JobParameters jobParameters = buildJobParameters(jobName, companyId);

            jobLauncher.run(job, jobParameters);

            log.info("Successfully completed Spring Batch Job: {} companyId={}", jobName, companyId);

        } catch (Exception e) {
            log.error("Failed to execute Spring Batch Job: {} companyId={}", jobName, companyId, e);
            throw new JobExecutionException("Batch Job Execution Failed: " + jobName, e);
        }
    }

    //  BATCH_JOB_EXECUTION_PARAMS
    private JobParameters buildJobParameters(String jobName, String companyId) {
        JobParametersBuilder builder = new JobParametersBuilder()
                .addDate("time", new Date());
        if (companyId != null && !companyId.isBlank()) {
            builder.addString("companyId", companyId);
        }

        LocalDate today = LocalDate.now(KST);
        switch (jobName) {
            case "payrollCalculateJob":
            case "severancePayJob":
            case "payslipSendJob":
            case "monthlyAttendanceCloseJob":
                builder.addString("yearMonth", today.format(YEAR_MONTH_FMT));
                break;
            case "leaveGrantJob":
            case "carryoverLeaveJob":
            case "leaveExpireJob":
            case "leavePromotionJob":
            case "unusedLeaveAutoPayoutJob":
            case "dailyAttendanceDraftJob":
            case "dailyAttendanceFinalJob":
            case "weeklyLimitCheckJob":
            case "slotDeadlineAutoAssignJob":
            case "regularBonusPaymentJob":
                builder.addString("date", today.toString());
                break;
            default:
                // time 만
                break;
        }
        return builder.toJobParameters();
    }
}
