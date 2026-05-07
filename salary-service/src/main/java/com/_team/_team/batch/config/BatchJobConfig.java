package com._team._team.batch.config;

import com._team._team.batch.attendance.worker.MonthlyAttendanceCloseWorker;
import com._team._team.batch.attendance.worker.OvertimeExpirationWorker;
import com._team._team.batch.attendance.worker.SlotDeadlineAutoAssignWorker;
import com._team._team.batch.attendance.worker.WeeklyLimitCheckWorker;
import com._team._team.batch.bonus.worker.RegularBonusPaymentWorker;
import com._team._team.batch.leave.worker.*;
import com._team._team.batch.payroll.worker.PayrollCalculateWorker;
import com._team._team.batch.payroll.worker.PayslipSendWorker;
import com._team._team.batch.payroll.worker.SeverancePayWorker;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Spring Batch Job / Step 빈 정의
 */
@Configuration
@EnableBatchProcessing
public class BatchJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final LeaveGrantWorker leaveGrantWorker;
    private final DailyAttendanceCloseWorker dailyAttendanceCloseWorker;
    private final LeaveExpireWorker leaveExpireWorker;
    private final PayrollCalculateWorker payrollCalculateWorker;
    private final SeverancePayWorker severancePayWorker;
    private final PayslipSendWorker payslipSendWorker;
    private final CarryoverLeaveWorker carryoverLeaveWorker;
    private final WeeklyLimitCheckWorker weeklyLimitCheckWorker;
    private final OvertimeExpirationWorker overtimeExpirationWorker;
    private final SlotDeadlineAutoAssignWorker slotDeadlineAutoAssignWorker;
    private final MonthlyAttendanceCloseWorker monthlyAttendanceCloseWorker;
    private final LeavePromotionWorker leavePromotionWorker;
    private final UnusedLeaveAutoPayoutWorker unusedLeaveAutoPayoutWorker;
    private final RegularBonusPaymentWorker regularBonusPaymentWorker;

    @Autowired
    public BatchJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            LeaveGrantWorker leaveGrantWorker,
            DailyAttendanceCloseWorker dailyAttendanceCloseWorker,
            LeaveExpireWorker leaveExpireWorker,
            PayrollCalculateWorker payrollCalculateWorker,
            SeverancePayWorker severancePayWorker,
            PayslipSendWorker payslipSendWorker,
            CarryoverLeaveWorker carryoverLeaveWorker,
            WeeklyLimitCheckWorker weeklyLimitCheckWorker
            , OvertimeExpirationWorker overtimeExpirationWorker
            , SlotDeadlineAutoAssignWorker slotDeadlineAutoAssignWorker, MonthlyAttendanceCloseWorker monthlyAttendanceCloseWorker, LeavePromotionWorker leavePromotionWorker, UnusedLeaveAutoPayoutWorker unusedLeaveAutoPayoutWorker, RegularBonusPaymentWorker regularBonusPaymentWorker) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.leaveGrantWorker = leaveGrantWorker;
        this.dailyAttendanceCloseWorker = dailyAttendanceCloseWorker;
        this.leaveExpireWorker = leaveExpireWorker;
        this.payrollCalculateWorker = payrollCalculateWorker;
        this.severancePayWorker = severancePayWorker;
        this.payslipSendWorker = payslipSendWorker;
        this.carryoverLeaveWorker = carryoverLeaveWorker;
        this.weeklyLimitCheckWorker = weeklyLimitCheckWorker;
        this.overtimeExpirationWorker = overtimeExpirationWorker;
        this.slotDeadlineAutoAssignWorker = slotDeadlineAutoAssignWorker;
        this.monthlyAttendanceCloseWorker = monthlyAttendanceCloseWorker;
        this.leavePromotionWorker = leavePromotionWorker;
        this.unusedLeaveAutoPayoutWorker = unusedLeaveAutoPayoutWorker;
        this.regularBonusPaymentWorker = regularBonusPaymentWorker;
    }

    /** 휴가 부여 Job */
    @Bean
    public Job leaveGrantJob() {
        return new JobBuilder("leaveGrantJob", jobRepository)
                .start(leaveGrantStep())
                .build();
    }

    // 휴가 부여 Step, companyId 있으면 회사별 처리
    @Bean
    public Step leaveGrantStep() {
        return new StepBuilder("leaveGrantStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String companyIdStr = chunkContext.getStepContext()
                            .getJobParameters().get("companyId") instanceof String s ? s : null;
                    if (companyIdStr != null && !companyIdStr.isBlank()) {
                        leaveGrantWorker.runForCompany(UUID.fromString(companyIdStr), LocalDate.now());
                    } else {
                        leaveGrantWorker.run(LocalDate.now());
                    }
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /** 일일 근태 마감 DRAFT Job (06:00 알림 단계) */
    @Bean
    public Job dailyAttendanceDraftJob() {
        return new JobBuilder("dailyAttendanceDraftJob", jobRepository)
                .start(dailyAttendanceDraftStep())
                .build();
    }

    /** 일일 근태 마감 DRAFT Step */
    @Bean
    public Step dailyAttendanceDraftStep() {
        return new StepBuilder("dailyAttendanceDraftStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    dailyAttendanceCloseWorker.runDraft();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /** 일일 근태 마감 FINAL Job (14:00 자동 마감 단계) */
    @Bean
    public Job dailyAttendanceFinalJob() {
        return new JobBuilder("dailyAttendanceFinalJob", jobRepository)
                .start(dailyAttendanceFinalStep())
                .build();
    }

    /** 일일 근태 마감 FINAL Step */
    @Bean
    public Step dailyAttendanceFinalStep() {
        return new StepBuilder("dailyAttendanceFinalStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    dailyAttendanceCloseWorker.runFinal();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /** 휴가 만료 Job */
    @Bean
    public Job leaveExpireJob() {
        return new JobBuilder("leaveExpireJob", jobRepository)
                .start(leaveExpireStep())
                .build();
    }

    /** 휴가 만료 Step */
    @Bean
    public Step leaveExpireStep() {
        return new StepBuilder("leaveExpireStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    leaveExpireWorker.run();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /** 급여 계산 Job */
    @Bean
    public Job payrollCalculateJob() {
        return new JobBuilder("payrollCalculateJob", jobRepository)
                .start(payrollCalculateStep())
                .build();
    }

    /**
     * 급여 계산 Step - 회사별 트리거에서 호출 시 그 회사만 처리
     */
    @Bean
    public Step payrollCalculateStep() {
        return new StepBuilder("payrollCalculateStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String companyIdStr = chunkContext.getStepContext()
                            .getJobParameters().get("companyId") instanceof String s ? s : null;
                    if (companyIdStr != null && !companyIdStr.isBlank()) {
                        payrollCalculateWorker.runForCompany(UUID.fromString(companyIdStr), null);
                    } else {
                        payrollCalculateWorker.run();
                    }
                    return RepeatStatus.FINISHED;
                }, new ResourcelessTransactionManager())
                .build();
    }

    /** 퇴직금 계산 Job */
    @Bean
    public Job severancePayJob() {
        return new JobBuilder("severancePayJob", jobRepository)
                .start(severancePayStep())
                .build();
    }

    // 퇴직금 계산 Step, companyId 있으면 회사별
    @Bean
    public Step severancePayStep() {
        return new StepBuilder("severancePayStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String companyIdStr = chunkContext.getStepContext()
                            .getJobParameters().get("companyId") instanceof String s ? s : null;
                    if (companyIdStr != null && !companyIdStr.isBlank()) {
                        severancePayWorker.runForCompany(UUID.fromString(companyIdStr));
                    } else {
                        severancePayWorker.run();
                    }
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /** 급여명세서 발송 Job */
    @Bean
    public Job payslipSendJob() {
        return new JobBuilder("payslipSendJob", jobRepository)
                .start(payslipSendStep())
                .build();
    }

    // 급여명세서 발송 Step, companyId 있으면 회사별
    @Bean
    public Step payslipSendStep() {
        return new StepBuilder("payslipSendStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String companyIdStr = chunkContext.getStepContext()
                            .getJobParameters().get("companyId") instanceof String s ? s : null;
                    if (companyIdStr != null && !companyIdStr.isBlank()) {
                        payslipSendWorker.runForCompany(UUID.fromString(companyIdStr));
                    } else {
                        payslipSendWorker.run();
                    }
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /** 연차 초과 부여 Job */
    @Bean
    public Job carryoverLeaveJob() {
        return new JobBuilder("carryoverLeaveJob", jobRepository)
                .start(carryoverLeaveStep())
                .build();
    }

    // 이월 연차 Step, companyId 있으면 회사별
    @Bean
    public Step carryoverLeaveStep() {
        return new StepBuilder("carryoverLeaveStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String companyIdStr = chunkContext.getStepContext()
                            .getJobParameters().get("companyId") instanceof String s ? s : null;
                    if (companyIdStr != null && !companyIdStr.isBlank()) {
                        carryoverLeaveWorker.runForCompany(UUID.fromString(companyIdStr), LocalDate.now());
                    } else {
                        carryoverLeaveWorker.run(LocalDate.now());
                    }
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /** 주 52시간 초과 감지 Job */
    @Bean
    public Job weeklyLimitCheckJob() {
        return new JobBuilder("weeklyLimitCheckJob", jobRepository)
                .start(weeklyLimitCheckStep())
                .build();
    }

    /** 주 52시간 초과 감지 Step */
    @Bean
    public Step weeklyLimitCheckStep() {
        return new StepBuilder("weeklyLimitCheckStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    weeklyLimitCheckWorker.run();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /** 사후 초과근무 신청 72시간 만료 Job */
    @Bean
    public Job overtimeExpirationJob() {
        return new JobBuilder("overtimeExpirationJob", jobRepository)
                .start(overtimeExpirationStep())
                .build();
    }

    /** 사후 초과근무 신청 72시간 만료 Step */
    @Bean
    public Step overtimeExpirationStep() {
        return new StepBuilder("overtimeExpirationStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    overtimeExpirationWorker.run();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /** 슬롯 선택 마감일 경과 자동 할당 Job */
    @Bean
    public Job slotDeadlineAutoAssignJob() {
        return new JobBuilder("slotDeadlineAutoAssignJob", jobRepository)
                .start(slotDeadlineAutoAssignStep())
                .build();
    }

    /** 슬롯 선택 마감일 경과 자동 할당 Step */
    @Bean
    public Step slotDeadlineAutoAssignStep() {
        return new StepBuilder("slotDeadlineAutoAssignStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    slotDeadlineAutoAssignWorker.run();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /** 월 장부 마감 Job */
    @Bean
    public Job monthlyAttendanceCloseJob() {
        return new JobBuilder("monthlyAttendanceCloseJob", jobRepository)
                .start(monthlyAttendanceCloseStep())
                .build();
    }

    // 월 장부 마감 Step, companyId 있으면 회사별
    @Bean
    public Step monthlyAttendanceCloseStep() {
        return new StepBuilder("monthlyAttendanceCloseStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String companyIdStr = chunkContext.getStepContext()
                            .getJobParameters().get("companyId") instanceof String s ? s : null;
                    if (companyIdStr != null && !companyIdStr.isBlank()) {
                        monthlyAttendanceCloseWorker.runForCompany(UUID.fromString(companyIdStr));
                    } else {
                        monthlyAttendanceCloseWorker.run();
                    }
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /** 연차사용촉진제 알림 Job */
    @Bean
    public Job leavePromotionJob() {
        return new JobBuilder("leavePromotionJob", jobRepository)
                .start(leavePromotionStep())
                .build();
    }

    @Bean
    public Step leavePromotionStep() {
        return new StepBuilder("leavePromotionStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    leavePromotionWorker.run(LocalDate.now());
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /** 정기 상여 지급일 알림 Job - 매일 실행, 회사별 지급 월/일 검증 후 자격 통과 직원에게 알림 */
    @Bean
    public Job regularBonusPaymentJob() {
        return new JobBuilder("regularBonusPaymentJob", jobRepository)
                .start(regularBonusPaymentStep())
                .build();
    }

    // 정기 상여 알림 Step, companyId 있으면 회사별
    @Bean
    public Step regularBonusPaymentStep() {
        return new StepBuilder("regularBonusPaymentStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String companyIdStr = chunkContext.getStepContext()
                            .getJobParameters().get("companyId") instanceof String s ? s : null;
                    if (companyIdStr != null && !companyIdStr.isBlank()) {
                        regularBonusPaymentWorker.runForCompany(UUID.fromString(companyIdStr), LocalDate.now());
                    } else {
                        regularBonusPaymentWorker.run(LocalDate.now());
                    }
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /** 미사용 연차 수당 자동 정산 Job (매일 만료 임박분 처리) */
    @Bean
    public Job unusedLeaveAutoPayoutJob() {
        return new JobBuilder("unusedLeaveAutoPayoutJob", jobRepository)
                .start(unusedLeaveAutoPayoutStep())
                .build();
    }

    @Bean
    public Step unusedLeaveAutoPayoutStep() {
        return new StepBuilder("unusedLeaveAutoPayoutStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    unusedLeaveAutoPayoutWorker.run(LocalDate.now());
                    return RepeatStatus.FINISHED;
                }, new ResourcelessTransactionManager())
                .build();
    }
}
