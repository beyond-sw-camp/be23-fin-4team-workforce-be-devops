package com._team._team.approval.service;

import com._team._team.approval.domain.enums.RequestType;
import com._team._team.approval.repository.ApprovalRequestRepository;
import com._team._team.dto.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class OfficialNumberService {

    private final ApprovalRequestRepository approvalRequestRepository;

    @Autowired
    public OfficialNumberService(ApprovalRequestRepository approvalRequestRepository) {
        this.approvalRequestRepository = approvalRequestRepository;
    }

    /**
     * 문서 번호 생성: {타입 접두어}-{YYYY}-{순번 4자리}
     * 예: 연차-2026-0001
     *
     * - 년도별, 타입별 번호 부여된 문서 수 + 1
     * - DB unique 제약으로 중복 방지, 충돌 시 @Retryable 로 재시도
     */
    @Retryable(
            retryFor = DataIntegrityViolationException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 50, multiplier = 1.5)
    )
    public String generate(RequestType requestType) {
        int year = LocalDate.now().getYear();
        String prefix = resolvePrefix(requestType);

        long count = approvalRequestRepository.countByRequestTypeInYear(requestType, year);

        long seq = count + 1;
        String number = String.format("%s-%d-%04d", prefix, year, seq);

        log.info("문서 번호 생성: number={}", number);
        return number;
    }

    private String resolvePrefix(RequestType type) {
        return switch (type) {
            case VACATION      -> "휴가";
            case ATTENDANCE    -> "근태";
            case HR            -> "인사";
            case BUSINESS_TRIP -> "출장";
            case GENERAL       -> "일반기안";
            case OFFICIAL      -> "공문";
        };
    }
}
