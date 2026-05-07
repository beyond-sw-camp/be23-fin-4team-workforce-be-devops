package com._team._team.attendance.service;

import com._team._team.attendance.domain.CompanyLeaveType;
import com._team._team.attendance.domain.enums.BalanceType;
import com._team._team.attendance.dto.reqDto.CompanyLeaveTypeCreateReqDto;
import com._team._team.attendance.dto.reqDto.CompanyLeaveTypeUpdateReqDto;
import com._team._team.attendance.publisher.RagSyncLeaveEventPublisher;
import com._team._team.attendance.repository.CompanyLeaveTypeRepository;
import com._team._team.dto.BusinessException;
import com._team._team.event.RagSyncLeaveEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

@Service
@Transactional
public class CompanyLeaveTypeService {

    private final CompanyLeaveTypeRepository repository;
    private final RagSyncLeaveEventPublisher ragSyncLeaveEventPublisher;

    @Autowired
    public CompanyLeaveTypeService(CompanyLeaveTypeRepository repository, RagSyncLeaveEventPublisher ragSyncLeaveEventPublisher) {
        this.repository = repository;
        this.ragSyncLeaveEventPublisher = ragSyncLeaveEventPublisher;
    }

    /**
     * 시스템 기본 휴가 추가
     *  지정 시 해당 코드만 시드 (가입 마법사 / 기본 휴가 불러오기 선택)
     */
    public void initializeDefaults(UUID companyId, Set<String> selectedCodes) {

        List<DefaultSpec> specs = List.of(
                // 기본 연차
                new DefaultSpec("ANNUAL",           "연차",               BalanceType.ANNUAL, 1.0, "Y", null, "N", 1),
                new DefaultSpec("HALF_AM",          "반차(오전)",          BalanceType.ANNUAL, 0.5, "Y", null, "N", 2),
                new DefaultSpec("HALF_PM",          "반차(오후)",          BalanceType.ANNUAL, 0.5, "Y", null, "N", 3),

                // 경조사
                new DefaultSpec("BEREAVEMENT",      "경조휴가(일반)",       null,               1.0, "Y", null, "Y", 4),

                // 경조사
                new DefaultSpec("MARRIAGE_SELF",    "결혼(본인)",          null,               1.0, "Y", 5.0,  "Y", 5),
                new DefaultSpec("MARRIAGE_CHILD",   "결혼(자녀)",          null,               1.0, "Y", 1.0,  "Y", 6),
                new DefaultSpec("BIRTH_SPOUSE",     "배우자 출산휴가",      null,               1.0, "Y", 10.0, "Y", 7),
                new DefaultSpec("MATERNITY",        "출산휴가",            null,               1.0, "Y", 90.0, "Y", 8),
                new DefaultSpec("DEATH_PARENT",     "부모 사망",           null,               1.0, "Y", 5.0,  "Y", 9),
                new DefaultSpec("DEATH_SPOUSE",     "배우자 사망",          null,               1.0, "Y", 5.0,  "Y", 10),
                new DefaultSpec("DEATH_CHILD",      "자녀 사망",           null,               1.0, "Y", 3.0,  "Y", 11),
                new DefaultSpec("DEATH_SIBLING",    "형제자매 사망",        null,               1.0, "Y", 3.0,  "Y", 12),

                // 기타 법정
                new DefaultSpec("PUBLIC",           "공가",                null,               1.0, "Y", null, "N", 13),
                new DefaultSpec("SICK",             "병가",                null,               1.0, "Y", 15.0, "Y", 14),
                new DefaultSpec("RESERVE_TRAINING", "예비군",              null,               1.0, "Y", null, "Y", 15),
                new DefaultSpec("CIVIL_DEFENSE",    "민방위",              null,               1.0, "Y", null, "Y", 16),

                // 여성 보호
                new DefaultSpec("MENSTRUATION",     "생리휴가",            null,               1.0, "N", 12.0, "N", 17)
        );

        boolean filterByCodes = selectedCodes != null && !selectedCodes.isEmpty();

        List<CompanyLeaveType> toSave = new ArrayList<>();
        int restored = 0;
        for (DefaultSpec spec : specs) {
            // 선택 시드 모드일 때 미선택 코드는 SKIP
            if (filterByCodes && !selectedCodes.contains(spec.code())) {
                continue;
            }
            // 이미 활성 행 존재 시 SKIP (사용자 수정 보존)
            if (repository.findByCompanyIdAndCodeAndDelYn(companyId, spec.code(), "N").isPresent()) {
                continue;
            }
            // 소프트 삭제만 된 행 기본값으로 복구
            Optional<CompanyLeaveType> tombstone =
                    repository.findByCompanyIdAndCode(companyId, spec.code());
            if (tombstone.isPresent()) {
                CompanyLeaveType row = tombstone.get();
                if ("Y".equals(row.getDelYn())) {
                    row.restoreSystemDefault(
                            spec.name(),
                            spec.balanceType(),
                            spec.daysPerUse(),
                            spec.isPaidYn(),
                            spec.maxDaysPerYear(),
                            spec.requireEvidenceYn(),
                            spec.displayOrder());
                    restored++;
                }
                continue;
            }
            toSave.add(CompanyLeaveType.builder()
                    .companyId(companyId)
                    .code(spec.code())
                    .name(spec.name())
                    .balanceType(spec.balanceType())
                    .daysPerUse(spec.daysPerUse())
                    .isSystemDefault(true)
                    .isPaidYn(spec.isPaidYn())
                    .maxDaysPerYear(spec.maxDaysPerYear())
                    .requireEvidenceYn(spec.requireEvidenceYn())
                    .displayOrder(spec.displayOrder())
                    .build());
        }
        if (!toSave.isEmpty()) {
            repository.saveAll(toSave);
        }
        if (!toSave.isEmpty() || restored > 0) {
            // 기본 휴가 시드 또는 삭제분 복구 후 RAG 동기화 이벤트 발행
            ragSyncLeaveEventPublisher.publish(
                    RagSyncLeaveEvent.builder()
                            .eventId(UUID.randomUUID())
                            .companyId(companyId)
                            .action("BULK")
                            .resourceType("COMPANY_LEAVE_TYPE")
                            .resourceId(null)
                            .timestamp(Instant.now())
                            .triggeredBy("system")
                            .build()
            );
        }
    }

    private record DefaultSpec(
            String code,
            String name,
            BalanceType balanceType,
            Double daysPerUse,
            String isPaidYn,
            Double maxDaysPerYear,
            String requireEvidenceYn,
            Integer displayOrder
    ) {}

    /**
     * 관리자 커스텀 휴가 생성 - 캐시 무효화
     */
    @CacheEvict(value = "companyLeaveTypes", key = "#companyId")
    public CompanyLeaveType create(UUID companyId, CompanyLeaveTypeCreateReqDto reqDto) {
        String resolvedCode = resolveCodeForCreate(companyId, reqDto.getCode(), reqDto.getName());

        repository.findByCompanyIdAndCodeAndDelYn(companyId, resolvedCode, "N")
                .ifPresent(existing -> {
                    throw new BusinessException(HttpStatus.CONFLICT,
                            "이미 존재하는 휴가 코드입니다: " + resolvedCode);
                });

        CompanyLeaveType entity = CompanyLeaveType.builder()
                .companyId(companyId)
                .code(resolvedCode)
                .name(reqDto.getName())
                .balanceType(reqDto.getBalanceType())
                .daysPerUse(reqDto.getDaysPerUse())
                .isSystemDefault(false)
                .isPaidYn(reqDto.getIsPaidYn())
                .maxDaysPerYear(reqDto.getMaxDaysPerYear())
                .requireEvidenceYn(reqDto.getRequireEvidenceYn())
                .displayOrder(reqDto.getDisplayOrder())
                .build();

        CompanyLeaveType saved = repository.save(entity);

        // RAG 동기화 이벤트 발행
        ragSyncLeaveEventPublisher.publish(
                RagSyncLeaveEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("CREATED")
                        .resourceType("COMPANY_LEAVE_TYPE")
                        .resourceId(saved.getCompanyLeaveTypeId())
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );

        return saved;
    }

    // 연차/반차는 제한된 수정만 허용하기 위한 코드 리스트
    private static final List<String> RESTRICTED_CODES = List.of(
            "ANNUAL", "HALF_AM", "HALF_PM"
    );

    private String resolveCodeForCreate(UUID companyId, String requestedCode, String name) {
        String trimmed = requestedCode == null ? "" : requestedCode.trim();
        if (!trimmed.isEmpty()) {
            return trimmed;
        }
        String base = buildCodeBaseFromName(name);
        String candidate = base;
        int seq = 2;
        while (repository.findByCompanyIdAndCodeAndDelYn(companyId, candidate, "N").isPresent()) {
            candidate = withAlphabeticSuffix(base, seq);
            seq += 1;
        }
        return candidate;
    }

    private String withAlphabeticSuffix(String base, int seq) {
        String suffix = "_AUTO" + "X".repeat(Math.max(0, seq - 2));
        String candidate = base + suffix;
        return candidate.length() > 50 ? candidate.substring(0, 50) : candidate;
    }

    private String buildCodeBaseFromName(String name) {
        if (name == null || name.isBlank()) return "CUSTOM";
        String normalized = name.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9가-힣]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (normalized.isBlank()) return "CUSTOM";
        // 백엔드 검증 패턴(대문자+언더스코어)에 맞추기 위해 한글/숫자 등은 제거 후 재정규화
        String strict = normalized.replaceAll("[^A-Z_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (strict.isBlank()) return "CUSTOM";
        return strict.length() > 50 ? strict.substring(0, 50) : strict;
    }

    /**
     * 수정, 시스템 기본은 name, displayOrder 만 허용 - 캐시 무효화
     */
    @CacheEvict(value = "companyLeaveTypes", key = "#companyId")
    public CompanyLeaveType update(UUID companyId, UUID companyLeaveTypeId,
                                   CompanyLeaveTypeUpdateReqDto reqDto) {
        CompanyLeaveType entity = findActive(companyId, companyLeaveTypeId);

        if (Boolean.TRUE.equals(entity.getIsSystemDefault())) {
            // 연차/반차는 수정 제한 - name, displayOrder만
            if (RESTRICTED_CODES.contains(entity.getCode())) {
                entity.update(
                        reqDto.getName(),
                        entity.getIsPaidYn(),            // 기존 값 유지
                        entity.getMaxDaysPerYear(),      // 기존 값 유지
                        entity.getRequireEvidenceYn(),   // 기존 값 유지
                        reqDto.getDisplayOrder()
                );
            } else {
                // 그 외 시스템 기본 휴가는 일수/유급/증빙도 수정 가능
                entity.update(
                        reqDto.getName(),
                        reqDto.getIsPaidYn(),
                        reqDto.getMaxDaysPerYear(),
                        reqDto.getRequireEvidenceYn(),
                        reqDto.getDisplayOrder()
                );
            }
        } else {
            // 커스텀 휴가는 모든 필드 수정 가능
            entity.updateCustom(
                    reqDto.getName(),
                    reqDto.getBalanceType(),
                    reqDto.getDaysPerUse(),
                    reqDto.getIsPaidYn(),
                    reqDto.getMaxDaysPerYear(),
                    reqDto.getRequireEvidenceYn(),
                    reqDto.getDisplayOrder());
        }

        // 레그 문서 Kafka 이벤트 발행
        ragSyncLeaveEventPublisher.publish(
                RagSyncLeaveEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("UPDATED")
                        .resourceType("COMPANY_LEAVE_TYPE")
                        .resourceId(companyLeaveTypeId)
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );
        return entity;
    }

    /**
     * 삭제, 시스템 기본은 불가 - 캐시 무효화
     */
    @CacheEvict(value = "companyLeaveTypes", key = "#companyId")
    public void delete(UUID companyId, UUID companyLeaveTypeId) {
        CompanyLeaveType entity = findActive(companyId, companyLeaveTypeId);
        entity.softDelete();

        // RAG 동기화 이벤트 발행
        ragSyncLeaveEventPublisher.publish(
                RagSyncLeaveEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("DELETED")
                        .resourceType("COMPANY_LEAVE_TYPE")
                        .resourceId(companyLeaveTypeId)
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );
    }

    // 회사 휴가 종류 list - 캐시 일시 비활성
    @Transactional(readOnly = true)
    public List<CompanyLeaveType> findAll(UUID companyId) {
        return repository.findAllByCompanyIdAndDelYnOrderByDisplayOrder(companyId, "N");
    }

    @Transactional(readOnly = true)
    public CompanyLeaveType findById(UUID companyId, UUID companyLeaveTypeId) {
        return findActive(companyId, companyLeaveTypeId);
    }

    // 휴가 신청 시 유효성 검증용
    @Transactional(readOnly = true)
    public CompanyLeaveType findActiveOrThrow(UUID companyId, UUID companyLeaveTypeId) {
        return findActive(companyId, companyLeaveTypeId);
    }

    private CompanyLeaveType findActive(UUID companyId, UUID companyLeaveTypeId) {
        return repository.findByCompanyLeaveTypeIdAndCompanyIdAndDelYn(
                        companyLeaveTypeId, companyId, "N")
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "휴가 종류를 찾을 수 없습니다."));
    }

    private CompanyLeaveType buildDefault(UUID companyId, String code, String name,
                                          BalanceType balanceType, Double daysPerUse,
                                          String isPaidYn, Double maxDaysPerYear,
                                          String requireEvidenceYn, Integer displayOrder) {
        return CompanyLeaveType.builder()
                .companyId(companyId)
                .code(code)
                .name(name)
                .balanceType(balanceType)
                .daysPerUse(daysPerUse)
                .isSystemDefault(true)
                .isPaidYn(isPaidYn)
                .maxDaysPerYear(maxDaysPerYear)
                .requireEvidenceYn(requireEvidenceYn)
                .displayOrder(displayOrder)
                .build();
    }
}


