package com._team._team.saas.schedule;

import com._team._team.dto.BusinessException;
import com._team._team.saas.schedule.dto.SaasScheduleResDto;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * SaaS 운영자용 Quartz 스케줄 조회/수정/일시중지
 * listAll 은 qrtz_triggers 직접 SELECT (Quartz API 락 회피)
 */
@Slf4j
@Service
public class SaasScheduleService {

    final Scheduler scheduler;
    final JdbcTemplate jdbcTemplate;

    @Autowired
    public SaasScheduleService(Scheduler scheduler, JdbcTemplate jdbcTemplate) {
        this.scheduler = scheduler;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SaasScheduleResDto> listAll() {
        String sql =
                "SELECT t.JOB_NAME, t.JOB_GROUP, t.TRIGGER_NAME, t.TRIGGER_GROUP, " +
                "       t.NEXT_FIRE_TIME, t.PREV_FIRE_TIME, t.TRIGGER_STATE, " +
                "       c.CRON_EXPRESSION " +
                "FROM QRTZ_TRIGGERS t " +
                "LEFT JOIN QRTZ_CRON_TRIGGERS c " +
                "  ON t.SCHED_NAME = c.SCHED_NAME " +
                "  AND t.TRIGGER_NAME = c.TRIGGER_NAME " +
                "  AND t.TRIGGER_GROUP = c.TRIGGER_GROUP " +
                "WHERE t.SCHED_NAME = ? " +
                "ORDER BY t.JOB_GROUP, t.JOB_NAME";
        try {
            String schedName = scheduler.getSchedulerName();
            List<SaasScheduleResDto> result = new ArrayList<>();
            jdbcTemplate.query(sql, rs -> {
                String jobName = rs.getString("JOB_NAME");
                String jobGroup = rs.getString("JOB_GROUP");
                String triggerName = rs.getString("TRIGGER_NAME");
                String triggerGroup = rs.getString("TRIGGER_GROUP");
                long nextMs = rs.getLong("NEXT_FIRE_TIME");
                long prevMs = rs.getLong("PREV_FIRE_TIME");
                String state = rs.getString("TRIGGER_STATE");
                String cron = rs.getString("CRON_EXPRESSION");
                boolean paused = "PAUSED".equals(state) || "PAUSED_BLOCKED".equals(state);
                result.add(SaasScheduleResDto.builder()
                        .jobKey(jobName + "::" + jobGroup)
                        .triggerKey(triggerName + "::" + triggerGroup)
                        .cronExpression(cron)
                        .nextFireTime(nextMs > 0 ? new Date(nextMs) : null)
                        .previousFireTime(prevMs > 0 ? new Date(prevMs) : null)
                        .paused(paused)
                        .build());
            }, schedName);
            return result;
        } catch (SchedulerException e) {
            log.error("[SaaS][Schedule] schedulerName 조회 실패", e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "스케줄 조회 실패");
        } catch (RuntimeException e) {
            log.error("[SaaS][Schedule] 목록 조회 실패", e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "스케줄 조회 실패");
        }
    }

    public void updateCron(String jobKey, String newCron) {
        if (!CronExpression.isValidExpression(newCron)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "유효하지 않은 cron 표현식입니다.");
        }
        JobKey jk = parseJobKey(jobKey);
        try {
            if (!scheduler.checkExists(jk)) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "잡을 찾을 수 없습니다.");
            }
            List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jk);
            for (Trigger t : triggers) {
                CronTrigger newTrigger = TriggerBuilder.newTrigger()
                        .withIdentity(t.getKey())
                        .forJob(jk)
                        .withSchedule(CronScheduleBuilder.cronSchedule(newCron)
                                .inTimeZone(TimeZone.getTimeZone("Asia/Seoul")))
                        .build();
                scheduler.rescheduleJob(t.getKey(), newTrigger);
                log.info("[SaaS][Schedule] cron 변경 jobKey={} cron={}", jobKey, newCron);
            }
        } catch (SchedulerException e) {
            log.error("[SaaS][Schedule] 변경 실패 jobKey={}", jobKey, e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "스케줄 변경 실패");
        }
    }

    public void pause(String jobKey) {
        JobKey jk = parseJobKey(jobKey);
        try {
            if (!scheduler.checkExists(jk)) throw new BusinessException(HttpStatus.NOT_FOUND, "잡을 찾을 수 없습니다.");
            scheduler.pauseJob(jk);
            log.info("[SaaS][Schedule] 일시중지 jobKey={}", jobKey);
        } catch (SchedulerException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "일시중지 실패");
        }
    }

    public void resume(String jobKey) {
        JobKey jk = parseJobKey(jobKey);
        try {
            if (!scheduler.checkExists(jk)) throw new BusinessException(HttpStatus.NOT_FOUND, "잡을 찾을 수 없습니다.");
            scheduler.resumeJob(jk);
            log.info("[SaaS][Schedule] 재개 jobKey={}", jobKey);
        } catch (SchedulerException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "재개 실패");
        }
    }

    private JobKey parseJobKey(String jobKey) {
        String[] parts = jobKey.split("::");
        if (parts.length != 2) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "jobKey 형식이 잘못되었습니다.");
        }
        return JobKey.jobKey(parts[0], parts[1]);
    }
}
