package com._team._team.demo;

import com._team._team.approval.repository.ApprovalDocumentRepository;
import com._team._team.approval.service.ApprovalDocumentService;
import com._team._team.contract.repository.ContractTemplateRepository;
import com._team._team.contract.service.ContractSeedService;
import com._team._team.contract.service.ContractTemplateService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 1) 회사별 기본 템플릿 자동 생성 (회사 온보딩 시 누락분 보충)
 * 2) 연봉제 회사의 직원별 매년 SALARY Contract 시드 (SIGNED)
 *    - native query 로 직원/Salary 직접 조회
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(100)
public class DemoContractTemplateSeedRunner implements ApplicationRunner {

    private final ContractTemplateService contractTemplateService;
    private final ContractTemplateRepository contractTemplateRepository;
    private final ContractSeedService contractSeedService;
    private final ApprovalDocumentService approvalDocumentService;
    private final ApprovalDocumentRepository approvalDocumentRepository;

    @PersistenceContext
    private EntityManager em;

    @Override
    public void run(ApplicationArguments args) {
        // @Transactional 의도적으로 미적용:
        // ===== 1) 모든 회사에 기본 템플릿 보충 =====
        @SuppressWarnings("unchecked")
        List<Object> companyRows = em.createNativeQuery(
                "SELECT company_id FROM company WHERE status = 'ACTIVE'"
        ).getResultList();

        int created = 0, skipped = 0;
        for (Object row : companyRows) {
            UUID companyId = toUuid(row);
            if (companyId == null) continue;
            boolean hasAny = !contractTemplateRepository
                    .findByCompanyIdAndDelYn(companyId, "N").isEmpty();
            if (hasAny) { skipped++; continue; }
            try {
                contractTemplateService.initDefaultTemplates(companyId);
                created++;
                log.info("[CONTRACT-SEED] 기본 템플릿 4종 생성 companyId={}", companyId);
            } catch (Exception e) {
                log.warn("[CONTRACT-SEED] 템플릿 생성 실패 companyId={} - {}", companyId, e.getMessage());
            }
        }
        log.info("[CONTRACT-SEED] 템플릿 - 신규 {}개 회사, skip {}개 회사", created, skipped);

        // ===== 1-2) 모든 회사에 기본 결재 양식 보충 =====
        int docCreated = 0, docSkipped = 0;
        for (Object row : companyRows) {
            UUID companyId = toUuid(row);
            if (companyId == null) continue;
            boolean hasAny = !approvalDocumentRepository.findByCompanyId(companyId).isEmpty();
            if (hasAny) { docSkipped++; continue; }
            try {
                approvalDocumentService.initDefaultDocuments(companyId);
                docCreated++;
                log.info("[APPROVAL-SEED] 기본 결재 양식 생성 companyId={}", companyId);
            } catch (Exception e) {
                log.warn("[APPROVAL-SEED] 결재 양식 생성 실패 companyId={} - {}", companyId, e.getMessage());
            }
        }
        log.info("[APPROVAL-SEED] 결재 양식 - 신규 {}개 회사, skip {}개 회사", docCreated, docSkipped);

        // ===== 2) 연봉제 회사 직원별 매년 SALARY Contract 시드 =====
        seedSalaryContracts();
    }

    private void seedSalaryContracts() {
        // salary_policy.use_pay_grade_yn = 'N' 인 회사의 모든 salary 행 + 직원 정보 조인 조회
        // 컬럼/테이블명은 Hibernate snake_case 기본 매핑 가정
        String sql = """
                SELECT s.member_id, s.company_id, s.base_salary, s.effective_from,
                       m.name, m.sabun,
                       o.name AS org_name, jt.name AS job_title_name
                FROM salary s
                JOIN salary_policy sp ON sp.company_id = s.company_id
                                     AND sp.use_pay_grade_yn = 'N'
                                     AND sp.del_yn = 'N'
                JOIN member m ON m.member_id = s.member_id
                JOIN member_position mp ON mp.member_position_id = m.default_position_id
                JOIN organization o ON o.organization_id = mp.organization_id
                JOIN job_title jt ON jt.job_title_id = mp.job_title_id
                ORDER BY s.member_id, s.effective_from
                """;

        List<Object[]> rows;
        try {
            @SuppressWarnings("unchecked")
            List<Object[]> r = em.createNativeQuery(sql).getResultList();
            rows = r;
        } catch (Exception e) {
            log.warn("[CONTRACT-SEED] SALARY 시드 query 실패 - {}", e.getMessage());
            return;
        }

        int seeded = 0, failed = 0;
        for (Object[] row : rows) {
            try {
                UUID memberId = toUuid(row[0]);
                UUID companyId = toUuid(row[1]);
                long baseSalary = ((Number) row[2]).longValue();
                LocalDate effectiveFrom = toLocalDate(row[3]);
                String name = (String) row[4];
                String sabun = row[5] == null ? null : row[5].toString();
                String orgName = (String) row[6];
                String jobTitleName = (String) row[7];
                if (memberId == null || companyId == null || effectiveFrom == null) continue;
                int year = effectiveFrom.getYear();
                contractSeedService.seedSalaryContract(
                        companyId, memberId, year, baseSalary, effectiveFrom,
                        name, sabun, orgName, jobTitleName);
                seeded++;
            } catch (Exception e) {
                failed++;
                log.warn("[CONTRACT-SEED] SALARY contract 1건 실패 - {}", e.getMessage());
            }
        }
        log.info("[CONTRACT-SEED] SALARY contract - 시도 {}건 (실패 {})", seeded, failed);
    }

    private UUID toUuid(Object raw) {
        if (raw == null) return null;
        if (raw instanceof UUID u) return u;
        if (raw instanceof byte[] bytes && bytes.length == 16) {
            ByteBuffer bb = ByteBuffer.wrap(bytes);
            return new UUID(bb.getLong(), bb.getLong());
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate toLocalDate(Object raw) {
        if (raw == null) return null;
        if (raw instanceof LocalDate d) return d;
        if (raw instanceof Date d) return d.toLocalDate();
        if (raw instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        try {
            return LocalDate.parse(raw.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
