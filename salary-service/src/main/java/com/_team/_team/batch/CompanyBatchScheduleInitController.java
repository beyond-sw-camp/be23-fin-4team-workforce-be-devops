package com._team._team.batch;

import com._team._team.batch.config.BatchSchedulerProperties;
import com._team._team.dto.ApiResponse;
import com._team._team.dto.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

// 회사 신규 가입 시점에 member-service 가 Feign 으로 호출, 회사별 트리거 일괄 시드
@Slf4j
@RestController
@RequestMapping("/salary/internal/batch-schedule")
public class CompanyBatchScheduleInitController {

    private final BatchScheduler batchScheduler;
    private final BatchSchedulerProperties schedulerProperties;

    @Autowired
    public CompanyBatchScheduleInitController(BatchScheduler batchScheduler,
                                              BatchSchedulerProperties schedulerProperties) {
        this.batchScheduler = batchScheduler;
        this.schedulerProperties = schedulerProperties;
    }

    // 회사 가입 후 호출, perCompanyJobs 의 default cron 으로 회사별 트리거 N개 등록
    @PostMapping("/init")
    public ResponseEntity<?> init(@RequestParam UUID companyId) {
        Map<String, String> perCompanyJobs = schedulerProperties.getPerCompanyJobs();
        if (perCompanyJobs == null || perCompanyJobs.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.success(0, "회사별 잡 정의 없음"), HttpStatus.OK);
        }
        int count = 0;
        try {
            for (var e : perCompanyJobs.entrySet()) {
                batchScheduler.registerPerCompanyJob(e.getKey(), e.getValue(), companyId);
                count++;
            }
            log.info("[BATCH-SYNC] companyId={} 트리거 {}건 시드 완료", companyId, count);
            return new ResponseEntity<>(ApiResponse.success(count, "회사별 잡 시드 완료"), HttpStatus.OK);
        } catch (SchedulerException e) {
            log.error("[BATCH-SYNC] companyId={} 시드 실패", companyId, e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "회사별 잡 시드 실패");
        }
    }

    // 회사 비활성화/탈퇴 시 호출, 트리거 일괄 제거
    @PostMapping("/remove")
    public ResponseEntity<?> remove(@RequestParam UUID companyId) {
        try {
            batchScheduler.unscheduleAllForCompany(companyId);
            log.info("[BATCH-SYNC] companyId={} 트리거 일괄 제거 완료", companyId);
            return new ResponseEntity<>(ApiResponse.success(null, "회사별 잡 제거 완료"), HttpStatus.OK);
        } catch (SchedulerException e) {
            log.error("[BATCH-SYNC] companyId={} 제거 실패", companyId, e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "회사별 잡 제거 실패");
        }
    }
}
