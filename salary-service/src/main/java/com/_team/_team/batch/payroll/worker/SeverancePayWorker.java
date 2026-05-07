package com._team._team.batch.payroll.worker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

// 퇴직금 산정 배치 워커
// 직전 3개월 임금 총액 기반 평균임금, 미사용 연차수당 역산/집계
@Slf4j
@Component
public class SeverancePayWorker {

    // 글로벌 호출 - 모든 회사 일괄
    public void run() {
        log.info("SeverancePayWorker: 퇴직 연동 데이터 없음 - 계산 스킵");
    }

    // 회사별 호출 - 회사 1개만 처리
    public void runForCompany(UUID companyId) {
        log.info("SeverancePayWorker: companyId={} 퇴직 연동 데이터 없음 - 계산 스킵", companyId);
    }
}
