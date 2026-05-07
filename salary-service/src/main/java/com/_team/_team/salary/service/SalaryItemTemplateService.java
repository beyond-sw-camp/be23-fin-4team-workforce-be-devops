package com._team._team.salary.service;

import com._team._team.dto.BusinessException;
import com._team._team.event.RagSyncSalaryEvent;
import com._team._team.salary.domain.enums.TaxCategory;
import com._team._team.salary.publisher.RagSyncSalaryEventPublisher;
import com._team._team.salary.repository.SalaryItemTemplateRepository;
import com._team._team.salary.domain.SalaryItemTemplate;
import com._team._team.salary.dto.reqdto.SalaryItemTemplateCreateReqDto;
import com._team._team.salary.dto.reqdto.SalaryItemTemplateUpdateReqDto;
import com._team._team.salary.dto.resdto.SalaryItemTemplateResDto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com._team._team.salary.domain.enums.ItemType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 급여 항목 템플릿 서비스
 */
@Service
@Transactional
public class SalaryItemTemplateService {

    private final SalaryItemTemplateRepository salaryItemTemplateRepository;
    private final RagSyncSalaryEventPublisher ragSyncSalaryEventPublisher;

    /** 생성자 주입 */
    @Autowired
    public SalaryItemTemplateService(SalaryItemTemplateRepository salaryItemTemplateRepository, RagSyncSalaryEventPublisher ragSyncSalaryEventPublisher) {
        this.salaryItemTemplateRepository = salaryItemTemplateRepository;
        this.ragSyncSalaryEventPublisher = ragSyncSalaryEventPublisher;
    }

    /** 급여 항목 템플릿 생성 - 캐시 무효화 */
    @CacheEvict(value = "salaryItemTemplates", key = "#companyId")
    public SalaryItemTemplateResDto save(UUID companyId, SalaryItemTemplateCreateReqDto reqDto) {
        validateDuplicateItemName(companyId, reqDto.getItemName());

        SalaryItemTemplate template = reqDto.toEntity(companyId);
        SalaryItemTemplate saved = salaryItemTemplateRepository.save(template);

        // RAG 동기화 이벤트 발행
        ragSyncSalaryEventPublisher.publish(
                RagSyncSalaryEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("CREATED")
                        .resourceType("SALARY_ITEM_TEMPLATE")
                        .resourceId(saved.getSalaryItemTemplateId())
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );
        return SalaryItemTemplateResDto.fromEntity(saved);
    }

    /** 급여 항목 템플릿 단건 조회 */
    @Transactional(readOnly = true)
    public SalaryItemTemplateResDto findById(UUID companyId, UUID salaryItemTemplateId) {
        SalaryItemTemplate template = salaryItemTemplateRepository.findById(salaryItemTemplateId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "급여 항목 템플릿을 찾을 수 없습니다."));

        if (!template.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        return SalaryItemTemplateResDto.fromEntity(template);
    }

    /** 회사별 급여 항목 템플릿 목록 조회 (캐시 일시 비활성) */
    @Transactional(readOnly = true)
    public List<SalaryItemTemplateResDto> findByCompanyId(UUID companyId) {
        return salaryItemTemplateRepository.findByCompanyIdAndDelYn(companyId, "N").stream()
                .map(SalaryItemTemplateResDto::fromEntity)
                .toList();
    }

    /** 급여 항목 템플릿 수정 (더티체킹) - 캐시 무효화 */
    @CacheEvict(value = "salaryItemTemplates", key = "#companyId")
    public SalaryItemTemplateResDto update(UUID companyId, UUID salaryItemTemplateId, SalaryItemTemplateUpdateReqDto reqDto) {
        SalaryItemTemplate template = salaryItemTemplateRepository.findById(salaryItemTemplateId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "급여 항목 템플릿을 찾을 수 없습니다."));

        if (!template.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        template.update(reqDto);

        // RAG 동기화 이벤트 발행
        ragSyncSalaryEventPublisher.publish(
                RagSyncSalaryEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("UPDATED")
                        .resourceType("SALARY_ITEM_TEMPLATE")
                        .resourceId(salaryItemTemplateId)
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );
        return SalaryItemTemplateResDto.fromEntity(template);
    }

    /** 급여 항목 템플릿 삭제 - 캐시 무효화 */
    @CacheEvict(value = "salaryItemTemplates", key = "#companyId")
    public void delete(UUID companyId, UUID salaryItemTemplateId) {
        SalaryItemTemplate template = salaryItemTemplateRepository.findById(salaryItemTemplateId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "급여 항목 템플릿을 찾을 수 없습니다."));

        if (!template.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        template.softDelete();

        // RAG 동기화 이벤트 발행
        ragSyncSalaryEventPublisher.publish(
                RagSyncSalaryEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("DELETED")
                        .resourceType("SALARY_ITEM_TEMPLATE")
                        .resourceId(salaryItemTemplateId)
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );
    }

    /** 동일 이름 항목 중복 검증 */
    private void validateDuplicateItemName(UUID companyId, String itemName) {
        if (salaryItemTemplateRepository.existsByCompanyIdAndItemNameAndDelYn(companyId, itemName, "N")) {
            throw new BusinessException(HttpStatus.CONFLICT, "동일한 이름의 급여 항목 템플릿이 이미 존재합니다.");
        }
    }

    /**
     * 회사 표준 급여 항목 자동 생성, 이미 등록된 항목은 건너뛰고 없는 것만 추가
     * - 기본급/직책수당 (과세 일반)
     * - 식대/자가운전/보육수당/연구활동비 (비과세 각 월 20만원)
     */
    public SeedResult initializeDefaults(UUID companyId) {
        // applyToAll=Y 인 항목은 회사 공통
        // 식대/자가운전/보육수당/연구활동비/교통비 = 회사 공통 (정책 일률 지급)
        // 직책수당/자녀수당 = 개인 차등 (직급, 자녀수에 따라 직원마다 다름)
        List<DefaultSpec> specs = List.of(
                new DefaultSpec("기본급",         ItemType.EARNING, 10, "Y", TaxCategory.TAXABLE,         null,    "N", "Y"),
                new DefaultSpec("직책수당",       ItemType.EARNING, 20, "Y", TaxCategory.TAXABLE,         null,    "N", "Y"),
                new DefaultSpec("식대",           ItemType.EARNING, 30, "N", TaxCategory.MEAL,            200000L, "Y", "N"),
                new DefaultSpec("자가운전보조금", ItemType.EARNING, 40, "N", TaxCategory.VEHICLE_SELF,    200000L, "Y", "N"),
                new DefaultSpec("교통비",         ItemType.EARNING, 41, "Y", TaxCategory.TAXABLE,         null,    "N", "N"),
                new DefaultSpec("보육수당",       ItemType.EARNING, 50, "N", TaxCategory.CHILDCARE,       200000L, "Y", "N"),
                new DefaultSpec("자녀수당",       ItemType.EARNING, 51, "Y", TaxCategory.TAXABLE,         null,    "N", "N"),
                new DefaultSpec("연구활동비",     ItemType.EARNING, 60, "N", TaxCategory.RESEARCH,        200000L, "Y", "N"),
                new DefaultSpec("퇴직월 일할 급여", ItemType.EARNING, 80, "Y", TaxCategory.TAXABLE,         null,    "N", "N"),
                new DefaultSpec("퇴직금",          ItemType.EARNING, 81, "N", TaxCategory.ETC_NON_TAXABLE, null,    "N", "N"),
                new DefaultSpec("미사용 연차 수당", ItemType.EARNING, 82, "Y", TaxCategory.TAXABLE,         null,    "N", "N")
        );

        List<SalaryItemTemplate> toSave = new ArrayList<>();
        int skipped = 0;

        for (DefaultSpec spec : specs) {
            boolean exists = salaryItemTemplateRepository
                    .findByCompanyIdAndItemNameAndDelYn(companyId, spec.name(), "N")
                    .isPresent();
            if (exists) {
                skipped++;
                continue;
            }
            toSave.add(SalaryItemTemplate.builder()
                    .companyId(companyId)
                    .itemName(spec.name())
                    .itemType(spec.type())
                    .displayOrder(spec.order())
                    .isTaxableYn(spec.taxable())
                    .taxCategory(spec.category())
                    .defaultAmount(spec.defaultAmount())
                    .applyToAllYn(spec.applyToAll())
                    .isOrdinaryWageYn(spec.ordinaryWage())
                    .isSystemDefault(true)
                    .delYn("N")
                    .build());
        }

        if (!toSave.isEmpty()) {
            salaryItemTemplateRepository.saveAll(toSave);

            // RAG 동기화 이벤트 발행 (실제 저장된 경우만)
            ragSyncSalaryEventPublisher.publish(
                    RagSyncSalaryEvent.builder()
                            .eventId(UUID.randomUUID())
                            .companyId(companyId)
                            .action("BULK")
                            .resourceType("SALARY_ITEM_TEMPLATE")
                            .resourceId(null)
                            .timestamp(Instant.now())
                            .triggeredBy("system")
                            .build()
            );
        }

        String msg = toSave.isEmpty()
                ? "이미 모든 표준 항목이 등록되어 있습니다."
                : "신규 " + toSave.size() + "건 반영, " + skipped + "건 스킵";
        return new SeedResult(toSave.size(), msg);
    }

    private record DefaultSpec(
            String name,
            ItemType type,
            int order,
            String taxable,
            TaxCategory category,
            Long defaultAmount,
            String applyToAll,
            String ordinaryWage
    ) {}

    public record SeedResult(int created, String message) {}
}
