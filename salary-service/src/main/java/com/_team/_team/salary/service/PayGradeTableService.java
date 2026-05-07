package com._team._team.salary.service;

import com._team._team.dto.BusinessException;
import com._team._team.event.RagSyncSalaryEvent;
import com._team._team.salary.domain.PayGradeTable;
import com._team._team.salary.dto.reqdto.PayGradeTableBulkCreateReqDto;
import com._team._team.salary.dto.reqdto.PayGradeTableCreateReqDto;
import com._team._team.salary.dto.reqdto.PayGradeTableUpdateReqDto;
import com._team._team.salary.publisher.RagSyncSalaryEventPublisher;
import com._team._team.salary.repository.PayGradeTableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 호봉표 관리 서비스, 호봉 전용 (직급 무관)
 * - 인상 시 같은 호봉 활성 레코드 자동 마감 후 신규 발행
 * - 이력 추적을 위해 소프트 삭제
 */
@Service
@Transactional
public class PayGradeTableService {

    private final PayGradeTableRepository repository;
    private final RagSyncSalaryEventPublisher ragSyncSalaryEventPublisher;

    @Autowired
    public PayGradeTableService(PayGradeTableRepository repository,
                                RagSyncSalaryEventPublisher ragSyncSalaryEventPublisher) {
        this.repository = repository;
        this.ragSyncSalaryEventPublisher = ragSyncSalaryEventPublisher;
    }

    public PayGradeTable create(UUID companyId, PayGradeTableCreateReqDto reqDto) {
        Optional<PayGradeTable> existing = repository.findActiveWithNoEnd(
                companyId, reqDto.getStep());

        existing.ifPresent(prev -> {
            if (!prev.getEffectiveFrom().isBefore(reqDto.getEffectiveFrom())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "새 적용일은 이전 적용일 이후여야 합니다.");
            }
            prev.closeEffectivePeriod(reqDto.getEffectiveFrom().minusDays(1));
        });

        PayGradeTable newRow = PayGradeTable.builder()
                .companyId(companyId)
                .step(reqDto.getStep())
                .baseSalary(reqDto.getBaseSalary())
                .effectiveFrom(reqDto.getEffectiveFrom())
                .description(reqDto.getDescription())
                .build();

        PayGradeTable saved = repository.save(newRow);

        // RAG 동기화 이벤트 발행
        ragSyncSalaryEventPublisher.publish(
                RagSyncSalaryEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("CREATED")
                        .resourceType("PAY_GRADE_TABLE")
                        .resourceId(saved.getPayGradeTableId())
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );

        return saved;
    }

    public PayGradeTable update(UUID companyId, UUID payGradeTableId,
                                PayGradeTableUpdateReqDto reqDto) {
        PayGradeTable row = findActiveOrThrow(companyId, payGradeTableId);
        row.update(reqDto);

        // RAG 동기화 이벤트 발행
        ragSyncSalaryEventPublisher.publish(
                RagSyncSalaryEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("UPDATED")
                        .resourceType("PAY_GRADE_TABLE")
                        .resourceId(payGradeTableId)
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );

        return row;
    }

    public void delete(UUID companyId, UUID payGradeTableId) {
        PayGradeTable row = findActiveOrThrow(companyId, payGradeTableId);
        row.delete();

        // RAG 동기화 이벤트 발행
        ragSyncSalaryEventPublisher.publish(
                RagSyncSalaryEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("DELETED")
                        .resourceType("PAY_GRADE_TABLE")
                        .resourceId(payGradeTableId)
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );
    }

    /**
     * 일괄 등록, 같은 호봉 활성 레코드는 자동 마감 후 신규 발행
     */
    public BulkResult bulkCreate(UUID companyId, PayGradeTableBulkCreateReqDto reqDto) {
        List<PayGradeTableBulkCreateReqDto.Entry> entries = reqDto.getEntries();
        if (entries.isEmpty()) {
            return new BulkResult(0, 0);
        }

        LocalDate newFrom = reqDto.getEffectiveFrom();
        LocalDate prevEnd = newFrom.minusDays(1);

        // 현재 회사 활성 레코드 한 번에 조회
        Map<Integer, PayGradeTable> activeByStep = repository
                .findAllActiveNoEnd(companyId)
                .stream()
                .collect(Collectors.toMap(
                        PayGradeTable::getStep,
                        p -> p,
                        (a, b) -> a
                ));

        List<PayGradeTable> toSave = new ArrayList<>(entries.size());
        int created = 0;
        int replaced = 0;

        for (PayGradeTableBulkCreateReqDto.Entry entry : entries) {
            PayGradeTable existing = activeByStep.get(entry.getStep());

            if (existing != null) {
                if (!existing.getEffectiveFrom().isBefore(newFrom)) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST,
                            entry.getStep() + "호봉, 새 적용일은 이전 적용일 이후여야 합니다.");
                }
                existing.closeEffectivePeriod(prevEnd);
                replaced++;
            } else {
                created++;
            }

            toSave.add(PayGradeTable.builder()
                    .companyId(companyId)
                    .step(entry.getStep())
                    .baseSalary(entry.getBaseSalary())
                    .effectiveFrom(newFrom)
                    .description(entry.getDescription())
                    .build());
        }

        repository.saveAll(toSave);

        // RAG 동기화 이벤트 발행 (BULK)
        ragSyncSalaryEventPublisher.publish(
                RagSyncSalaryEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("BULK")
                        .resourceType("PAY_GRADE_TABLE")
                        .resourceId(null)
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );

        return new BulkResult(created, replaced);
    }

    @Transactional(readOnly = true)
    public List<PayGradeTable> findAllByCompany(UUID companyId) {
        return repository.findAllByCompany(companyId);
    }

    @Transactional(readOnly = true)
    public PayGradeTable findById(UUID companyId, UUID payGradeTableId) {
        return findActiveOrThrow(companyId, payGradeTableId);
    }

    private PayGradeTable findActiveOrThrow(UUID companyId, UUID payGradeTableId) {
        return repository.findByPayGradeTableIdAndCompanyIdAndDelYn(
                        payGradeTableId, companyId, "N")
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "호봉 정보를 찾을 수 없습니다."));
    }

    public record BulkResult(int created, int replaced) {}
}