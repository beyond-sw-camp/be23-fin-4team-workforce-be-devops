package com._team._team.salary.service;

import com._team._team.dto.BusinessException;
import com._team._team.salary.domain.MemberAllowance;
import com._team._team.salary.domain.Salary;
import com._team._team.salary.domain.SalaryItemTemplate;
import com._team._team.salary.domain.SalaryPolicy;
import com._team._team.salary.domain.enums.AllowanceApprovalStatus;
import com._team._team.salary.repository.MemberAllowanceRepository;
import com._team._team.salary.repository.SalaryItemTemplateRepository;
import com._team._team.salary.repository.SalaryPolicyRepository;
import com._team._team.salary.repository.SalaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class MemberAllowanceService {

    private static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

    private final MemberAllowanceRepository repository;
    private final SalaryRepository salaryRepository;
    private final SalaryItemTemplateRepository salaryItemTemplateRepository;
    private final SalaryPolicyRepository salaryPolicyRepository;
    private final PayrollService payrollService;

    @Autowired
    public MemberAllowanceService(MemberAllowanceRepository repository,
                                  SalaryRepository salaryRepository,
                                  SalaryItemTemplateRepository salaryItemTemplateRepository,
                                  SalaryPolicyRepository salaryPolicyRepository,
                                  @Lazy PayrollService payrollService) {
        this.repository = repository;
        this.salaryRepository = salaryRepository;
        this.salaryItemTemplateRepository = salaryItemTemplateRepository;
        this.salaryPolicyRepository = salaryPolicyRepository;
        this.payrollService = payrollService;
    }

    // 직원 입사 시 수당 선택하면 기본 수당 AUTO 등록, 결재 생략
    public MemberAllowance autoGrant(UUID memberId, UUID companyId,
                                     UUID templateId, Long amount,
                                     LocalDate effectiveFrom) {
        // 기존 활성 수당 close - 중복 row 가 있을 수 있으므로 모두 종료
        List<MemberAllowance> prev = repository.findCurrentByTemplate(memberId, companyId, templateId, effectiveFrom);
        for (MemberAllowance p : prev) {
            p.closeEffectivePeriod(effectiveFrom.minusDays(1));
        }

        LocalDateTime now = LocalDateTime.now();
        MemberAllowance entity = MemberAllowance.builder()
                .memberId(memberId)
                .companyId(companyId)
                .salaryItemTemplateId(templateId)
                .amount(amount)
                .effectiveFrom(effectiveFrom)
                .approvalStatus(AllowanceApprovalStatus.AUTO)
                .requestedBy(SYSTEM_ACTOR)
                .requestedAt(now)
                .reason("입사시 수당 등록")
                .build();

        return repository.save(entity);
    }

    // 본인 신청, 결재 대기 PENDING 생성
    public MemberAllowance requestChange(UUID memberId, UUID companyId,
                                         UUID templateId, Long amount,
                                         LocalDate effectiveFrom, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "변경 사유를 입력해주세요.");
        }

        LocalDateTime now = LocalDateTime.now();
        MemberAllowance entity = MemberAllowance.builder()
                .memberId(memberId)
                .companyId(companyId)
                .salaryItemTemplateId(templateId)
                .amount(amount)
                .effectiveFrom(effectiveFrom)
                .approvalStatus(AllowanceApprovalStatus.PENDING)
                .requestedBy(memberId)
                .requestedAt(now)
                .reason(reason)
                .build();

        return repository.save(entity);
    }

    // 결재 ID 연결, 프론트에서 수당신청(전자결재)시 호출
    public MemberAllowance linkApprovalRequest(UUID memberAllowanceId,
                                               UUID memberId,
                                               UUID approvalRequestId) {
        MemberAllowance entity = findOwn(memberAllowanceId, memberId);
        entity.linkApprovalRequest(approvalRequestId);
        return entity;
    }

    // 결재 승인 반영, Consumer 가 호출
    public void applyApproval(UUID approvalRequestId, UUID approverId, LocalDateTime decidedAt) {
        MemberAllowance entity = repository.findByApprovalRequestId(approvalRequestId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "해당 결재에 연결된 수당 신청을 찾을 수 없습니다."));

        // 승인 시점 회사 payDay 기준 effectiveFrom 자동 재산정 (1일 기준)
        // 승인일 < payDay : 당월 1일
        // 승인일 >= payDay : 다음달 1일
        // (월별 정산 미리보기 cutoff 가 월 1일 시점이라 1일에 맞춰야 정산 미리보기에 잡힘)
        LocalDate today = decidedAt.toLocalDate();
        Integer payDay = salaryPolicyRepository.findActivePolicies(entity.getCompanyId(), today)
                .stream().findFirst()
                .map(SalaryPolicy::getPayDay)
                .orElse(25);
        if (payDay == null) payDay = 25;
        LocalDate base = today.getDayOfMonth() < payDay ? today : today.plusMonths(1);
        LocalDate computedEffectiveFrom = base.withDayOfMonth(1);
        entity.rescheduleEffectiveFrom(computedEffectiveFrom);

        // 기존 유효 수당 종료, effectiveFrom 전날로 close (자기 자신 제외, 중복 row 모두 처리)
        List<MemberAllowance> prevList = repository.findCurrentByTemplate(
                entity.getMemberId(),
                entity.getCompanyId(),
                entity.getSalaryItemTemplateId(),
                entity.getEffectiveFrom());
        for (MemberAllowance prev : prevList) {
            if (!prev.getMemberAllowanceId().equals(entity.getMemberAllowanceId())) {
                prev.closeEffectivePeriod(entity.getEffectiveFrom().minusDays(1));
            }
        }

        entity.approve(approverId, decidedAt);
        log.info("[Allowance][Approve] memberId={} payDay={} effectiveFrom={}",
                entity.getMemberId(), payDay, computedEffectiveFrom);
    }

    // 결재 반려 반영
    public void applyRejection(UUID approvalRequestId, UUID approverId,
                               LocalDateTime decidedAt, String note) {
        MemberAllowance entity = repository.findByApprovalRequestId(approvalRequestId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "해당 결재에 연결된 수당 신청을 찾을 수 없습니다."));
        entity.reject(approverId, decidedAt, note);
    }

    // 본인 수당 신청 철회
    public void cancel(UUID memberAllowanceId, UUID memberId) {
        MemberAllowance entity = findOwn(memberAllowanceId, memberId);
        entity.cancel();
    }

    /**
     * 관리자가 특정 직원/템플릿의 활성 수당을 즉시 종료
     * 급여 수정 시 [부가 수당] 토글에서 체크 해제된 항목 처리
     */
    public int adminCloseByTemplate(UUID memberId, UUID companyId, UUID templateId, LocalDate closeAt) {
        List<MemberAllowance> active = repository.findCurrentByTemplate(memberId, companyId, templateId, closeAt);
        for (MemberAllowance e : active) {
            e.closeEffectivePeriod(closeAt);
        }
        return active.size();
    }

    /**
     * 관리자가 특정 직원/템플릿의 모든 활성 행을 일괄 소프트 삭제
     * 미래 effectiveFrom 의 수당을 토글 해제 시 - hard-delete
     */
    public int adminDeleteByTemplate(UUID memberId, UUID companyId, UUID templateId) {
        return repository.softDeleteByMemberAndTemplate(memberId, companyId, templateId);
    }

    /**
     * 관리자가 단건 수당을 종료 처리 + 이력 보존
     * [수당 관리] 화면의 칩 X 버튼에서 호출
     */
    public void adminCloseOne(UUID memberAllowanceId, UUID companyId, LocalDate closeAt) {
        MemberAllowance entity = repository.findById(memberAllowanceId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "수당 내역을 찾을 수 없습니다."));
        if (!entity.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "다른 회사의 수당입니다.");
        }
        if (entity.getEffectiveTo() != null
                && entity.getEffectiveTo().isBefore(LocalDate.now())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "이미 종료된 수당입니다.");
        }
        LocalDate at = closeAt == null ? LocalDate.now() : closeAt;
        if (entity.getEffectiveFrom() != null && at.isBefore(entity.getEffectiveFrom())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "종료일은 시작일 이후여야 합니다.");
        }
        entity.closeEffectivePeriod(at);

        // 영구 종료 후속 처리
        SalaryItemTemplate template = salaryItemTemplateRepository
                .findById(entity.getSalaryItemTemplateId())
                .orElse(null);
        if (template != null) {
            payrollService.deleteAllowanceItemsForMember(
                    companyId, entity.getMemberId(), template.getItemName());
        }
    }

    /**
     * 관리자가 단건 수당을 소프트 삭제 (실수 정정용)
     * [수당 관리] 화면의 행별 [완전 삭제] 버튼에서 호출
     */
    public void adminSoftDeleteOne(UUID memberAllowanceId, UUID companyId) {
        MemberAllowance entity = repository.findById(memberAllowanceId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "수당 내역을 찾을 수 없습니다."));
        if (!entity.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "다른 회사의 수당입니다.");
        }
        // 이미 종료된 과거 행은 history 보존을 위해 삭제 불가 - 현재/미래 행만 삭제 허용.
        if (entity.getEffectiveTo() != null
                && entity.getEffectiveTo().isBefore(LocalDate.now())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "이미 종료된 과거 수당은 삭제할 수 없습니다 (이력 보존).");
        }
        entity.softDelete();
    }

    // 급여 계산 시 호출, 활성 수당 전체 조회
    @Transactional(readOnly = true)
    public List<MemberAllowance> findActiveAt(UUID memberId, UUID companyId, LocalDate date) {
        return repository.findActiveByMemberAndDate(memberId, companyId, date);
    }

    private MemberAllowance findOwn(UUID memberAllowanceId, UUID memberId) {
        MemberAllowance entity = repository.findById(memberAllowanceId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "수당 신청 내역을 찾을 수 없습니다."));

        if (!entity.getMemberId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "본인 요청만 가능합니다.");
        }
        return entity;
    }

    // 본인 수당 이력 조회
    @Transactional(readOnly = true)
    public List<MemberAllowance> findMyHistory(UUID memberId, UUID companyId) {
        return repository.findByMemberIdAndCompanyIdAndDelYnOrderByEffectiveFromDesc(
                memberId, companyId, "N");
    }

    // 관리자, 상태별 회사 수당 조회
    @Transactional(readOnly = true)
    public List<MemberAllowance> findByCompanyAndStatus(
            UUID companyId, AllowanceApprovalStatus status) {
        return repository.findByCompanyIdAndApprovalStatusAndDelYnOrderByRequestedAtDesc(
                companyId, status, "N");
    }

    /**
     * 관리자 - 회사 전체 수당 조회
     */
    @Transactional(readOnly = true)
    public List<MemberAllowance> findCompanyActiveInMonth(
            UUID companyId, AllowanceApprovalStatus status, java.time.YearMonth ym) {
        java.time.LocalDate start = ym.atDay(1);
        java.time.LocalDate end = ym.atEndOfMonth();
        return status == null
                ? repository.findCompanyAllowancesActiveInMonth(companyId, start, end)
                : repository.findCompanyAllowancesActiveInMonthByStatus(companyId, status, start, end);
    }

    /**
     * 관리자 - 회사 전체 이력 (활성 + 종료, 효력일 역순)
     */
    @Transactional(readOnly = true)
    public List<MemberAllowance> findCompanyAllHistory(UUID companyId) {
        return repository.findCompanyAllHistory(companyId);
    }

    /**
     * 관리자 - 특정 직원 활성 수당 조회
     */
    @Transactional(readOnly = true)
    public List<MemberAllowance> findMemberActiveInMonth(
            UUID memberId, UUID companyId, java.time.YearMonth ym) {
        java.time.LocalDate start = ym.atDay(1);
        java.time.LocalDate end = ym.atEndOfMonth();
        return repository.findMemberAllowancesActiveInMonth(memberId, companyId, start, end);
    }

    /**
     * 회사 내 수당 일괄 정리
     * Salary 가 없으면 수당도 의미가 없어 일괄 소프트 삭제
     */
    public int cleanupOrphans(UUID companyId) {
        java.time.YearMonth currentMonth = java.time.YearMonth.now();
        LocalDate start = currentMonth.atDay(1);
        LocalDate end = currentMonth.atEndOfMonth();

        List<MemberAllowance> active = repository.findCompanyAllowancesActiveInMonth(companyId, start, end);
        Set<UUID> memberIds = new HashSet<>();
        for (MemberAllowance a : active) {
            if (a.getMemberId() != null) memberIds.add(a.getMemberId());
        }

        int totalClosed = 0;
        for (UUID memberId : memberIds) {
            List<Salary> salaries = salaryRepository.findByMemberIdAndCompanyId(memberId, companyId);
            if (salaries.isEmpty()) {
                int closed = repository.softDeleteAllByMember(memberId, companyId);
                totalClosed += closed;
                log.info("[ALLOWANCE-CLEANUP] orphan memberId={} companyId={} 행={} 정리",
                        memberId, companyId, closed);
            }
        }
        log.info("[ALLOWANCE-CLEANUP] 회사={} 총 {} 건 정리", companyId, totalClosed);
        return totalClosed;
    }
}