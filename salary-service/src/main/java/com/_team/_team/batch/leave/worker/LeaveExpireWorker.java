package com._team._team.batch.leave.worker;

import com._team._team.attendance.repository.MemberBalanceRepository;
import com._team._team.attendance.domain.MemberBalance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 휴가 잔액 MemberBalance의 만료일 경과에 따른 소멸·비가용 처리 배치용 워커
 */
@Slf4j
@Component
public class LeaveExpireWorker {

    private final MemberBalanceRepository memberBalanceRepository;

    @Autowired
    public LeaveExpireWorker(MemberBalanceRepository memberBalanceRepository) {
        this.memberBalanceRepository = memberBalanceRepository;
    }

    /** 만료 배치 실행 */
    @Transactional
    public void run() {
        LocalDate today = LocalDate.now();
        List<MemberBalance> targets = memberBalanceRepository.findExpirationTargets(today);
        if (targets.isEmpty()) {
            log.info("LeaveExpireWorker: 만료 대상 없음");
            return;
        }
        targets.forEach(MemberBalance::markExpired);
        memberBalanceRepository.saveAll(targets);
        log.info("LeaveExpireWorker: {}건 만료 처리", targets.size());
    }
}
