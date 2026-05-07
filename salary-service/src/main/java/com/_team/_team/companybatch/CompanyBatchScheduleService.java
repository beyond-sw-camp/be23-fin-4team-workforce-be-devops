package com._team._team.companybatch;

import com._team._team.batch.BatchScheduler;
import com._team._team.dto.BusinessException;
import com._team._team.saas.schedule.SaasScheduleService;
import com._team._team.saas.schedule.dto.SaasScheduleResDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

// 회사 관리자용 자동 작업 관리 서비스
@Slf4j
@Service
public class CompanyBatchScheduleService {

    private static final String SCHED_NAME = "salary-hr-batch";
    private final JdbcTemplate jdbcTemplate;
    private final SaasScheduleService saasScheduleService;

    @Autowired
    public CompanyBatchScheduleService(JdbcTemplate jdbcTemplate,
                                       SaasScheduleService saasScheduleService) {
        this.jdbcTemplate = jdbcTemplate;
        this.saasScheduleService = saasScheduleService;
    }

    // 회사 관리자 화면용 목록, 자기 회사 트리거만
    public List<SaasScheduleResDto> listForCompany(UUID companyId) {
        String suffix = BatchScheduler.COMPANY_DELIMITER + companyId.toString();
        String sql =
                "SELECT t.JOB_NAME, t.JOB_GROUP, t.TRIGGER_NAME, t.TRIGGER_GROUP, " +
                "       t.NEXT_FIRE_TIME, t.PREV_FIRE_TIME, t.TRIGGER_STATE, " +
                "       c.CRON_EXPRESSION " +
                "FROM QRTZ_TRIGGERS t " +
                "LEFT JOIN QRTZ_CRON_TRIGGERS c " +
                "  ON t.SCHED_NAME = c.SCHED_NAME " +
                "  AND t.TRIGGER_NAME = c.TRIGGER_NAME " +
                "  AND t.TRIGGER_GROUP = c.TRIGGER_GROUP " +
                "WHERE t.SCHED_NAME = ? AND t.JOB_NAME LIKE ? " +
                "ORDER BY t.JOB_NAME";
        try {
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
            }, SCHED_NAME, "%" + suffix + "%");
            return result;
        } catch (RuntimeException e) {
            log.error("[CompanyBatch] 목록 조회 실패 companyId={}", companyId, e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "스케줄 조회 실패");
        }
    }

    // 회사 권한 체크 후 cron 변경
    public void updateCron(UUID companyId, String jobKey, String newCron) {
        ensureOwnedByCompany(companyId, jobKey);
        saasScheduleService.updateCron(jobKey, newCron);
    }

    public void pause(UUID companyId, String jobKey) {
        ensureOwnedByCompany(companyId, jobKey);
        saasScheduleService.pause(jobKey);
    }

    public void resume(UUID companyId, String jobKey) {
        ensureOwnedByCompany(companyId, jobKey);
        saasScheduleService.resume(jobKey);
    }

    // jobKey 가 회사 소유인지 검증, jobName 에 __{companyId} 포함 여부로 판단
    private void ensureOwnedByCompany(UUID companyId, String jobKey) {
        if (jobKey == null || !jobKey.contains(BatchScheduler.COMPANY_DELIMITER + companyId.toString())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "다른 회사의 작업은 변경할 수 없습니다.");
        }
    }
}
