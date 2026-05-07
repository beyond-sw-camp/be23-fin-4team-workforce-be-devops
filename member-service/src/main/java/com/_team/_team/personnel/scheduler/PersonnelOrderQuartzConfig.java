package com._team._team.personnel.scheduler;

import com._team._team.personnel.service.PersonnelOrderApplyService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * 인사발령 자동 적용 배치
 */
@Slf4j
@Configuration
public class PersonnelOrderQuartzConfig {

    private static final String GROUP = "PERSONNEL_ORDER_GROUP";
    private static final String JOB_NAME = "personnelOrderApplyJob";

    /** 매일 00:05 KST */
    private static final String CRON_DAILY = "0 5 0 * * ?";

    private final Scheduler scheduler;
    private final PersonnelOrderApplyService applyService;

    @Autowired
    public PersonnelOrderQuartzConfig(Scheduler scheduler,
                                      PersonnelOrderApplyService applyService) {
        this.scheduler = scheduler;
        this.applyService = applyService;
    }

    @PostConstruct
    public void register() {
        try {
            TriggerKey triggerKey = TriggerKey.triggerKey(JOB_NAME + "_Trigger", GROUP);

            // 트리거 이미 있으면 그대로 - SaaS 운영자가 변경한 cron 보존
            if (scheduler.checkExists(triggerKey)) {
                log.info("[PersonnelOrder][Quartz] 기존 트리거 유지");
            } else {
                // 최초 등록만 yml 기본 cron 사용
                JobDetail jobDetail = JobBuilder.newJob(PersonnelOrderApplyJob.class)
                        .withIdentity(JOB_NAME + "_Detail", GROUP)
                        .storeDurably()
                        .build();

                Trigger trigger = TriggerBuilder.newTrigger()
                        .withIdentity(triggerKey)
                        .forJob(jobDetail)
                        .withSchedule(CronScheduleBuilder.cronSchedule(CRON_DAILY)
                                .inTimeZone(TimeZone.getTimeZone("Asia/Seoul")))
                        .build();
                scheduler.scheduleJob(jobDetail, trigger);
                log.info("[PersonnelOrder][Quartz] 최초 등록 완료 cron='{}'", CRON_DAILY);
            }
        } catch (SchedulerException e) {
            log.error("[PersonnelOrder][Quartz] 등록 실패", e);
        }

        // 부팅 직후 1회 보정 , 누락 방지
        try {
            int applied = applyService.applyDuePendingOrders();
            if (applied > 0) {
                log.info("[PersonnelOrder][Bootstrap] 부팅 보정 - {}건 적용", applied);
            }
        } catch (Exception e) {
            log.warn("[PersonnelOrder][Bootstrap] 부팅 보정 실패 - {}", e.getMessage());
        }
    }
}
