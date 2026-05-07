package com._team._team.contract.service;

import com._team._team.contract.domain.enums.ContractType;
import com._team._team.contract.repository.ContractRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@Transactional
public class ContractNumberService {

    private final ContractRepository contractRepository;

    @Autowired
    public ContractNumberService(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    @Retryable(
            retryFor = DataIntegrityViolationException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 50, multiplier = 1.5)
    )
    public String generate(ContractType contractType) {
        int year = LocalDate.now().getYear();
        String prefix = resolvePrefix(contractType);

        long count = contractRepository.countByContractTypeInYear(contractType, year);
        long seq = count + 1;
        String number = String.format("%s-%d-%04d", prefix, year, seq);

        log.info("계약 문서번호 생성: number={}", number);
        return number;
    }

    private String resolvePrefix(ContractType type) {
        return switch (type) {
            case EMPLOYMENT      -> "근로";
            case SALARY          -> "연봉";
            case NDA             -> "비밀유지";
            case PRIVACY_CONSENT -> "개인정보";
        };
    }
}
