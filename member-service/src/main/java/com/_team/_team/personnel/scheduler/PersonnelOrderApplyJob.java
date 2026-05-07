package com._team._team.personnel.scheduler;

import com._team._team.personnel.service.PersonnelOrderApplyService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * Quartz 트리거로 호출되는 Job
 */
@Slf4j
public class PersonnelOrderApplyJob extends QuartzJobBean {

    @Autowired
    private PersonnelOrderApplyService applyService;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        log.info("[PersonnelOrder][Quartz] 일별 미래 발령 자동 적용 배치 시작");
        try {
            int applied = applyService.applyDuePendingOrders();
            log.info("[PersonnelOrder][Quartz] 배치 완료 - {}건 적용", applied);
        } catch (Exception e) {
            log.error("[PersonnelOrder][Quartz] 배치 실행 실패", e);
            throw new JobExecutionException(e);
        }
    }
}
