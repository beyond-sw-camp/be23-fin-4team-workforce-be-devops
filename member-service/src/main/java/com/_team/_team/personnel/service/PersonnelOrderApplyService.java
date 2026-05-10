package com._team._team.personnel.service;

import com._team._team.company.feignclients.SalaryServiceClient;
import com._team._team.event.PersonnelOrderApprovedEvent;
import com._team._team.member.domain.Member;
import com._team._team.member.domain.MemberPosition;
import com._team._team.member.repository.MemberPositionRepository;
import com._team._team.member.repository.MemberRepository;
import com._team._team.organization.domain.JobGrade;
import com._team._team.organization.domain.JobTitle;
import com._team._team.organization.domain.Organization;
import com._team._team.organization.repository.JobGradeRepository;
import com._team._team.organization.repository.JobTitleRepository;
import com._team._team.organization.repository.OrganizationRepository;
import com._team._team.personnel.domain.PersonnelOrder;
import com._team._team.personnel.domain.enums.PersonnelOrderType;
import com._team._team.personnel.repository.PersonnelOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 인사발령 적용 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonnelOrderApplyService {

    private final PersonnelOrderRepository personnelOrderRepository;
    private final MemberRepository memberRepository;
    private final MemberPositionRepository memberPositionRepository;
    private final OrganizationRepository organizationRepository;
    private final JobGradeRepository jobGradeRepository;
    private final JobTitleRepository jobTitleRepository;
    // 발령 적용 후 salary-service 의 활성 Salary 직급/직책 동기화 + 호봉 reset 호출용
    private final SalaryServiceClient salaryServiceClient;

    @Transactional
    public void apply(PersonnelOrderApprovedEvent event) {
        if (event == null || event.getItems() == null || event.getItems().isEmpty()) {
            log.warn("[PersonnelOrder] empty event - skipped");
            return;
        }
        LocalDate effectiveDate = event.getEffectiveDate() != null
                ? event.getEffectiveDate() : LocalDate.now();
        for (PersonnelOrderApprovedEvent.Item item : event.getItems()) {
            try {
                applyOne(event, item, effectiveDate);
            } catch (Exception e) {
                log.error("[PersonnelOrder] item apply failed - memberId={} - {}",
                        item.getMemberId(), e.getMessage(), e);
            }
        }
    }

    private void applyOne(PersonnelOrderApprovedEvent event,
                          PersonnelOrderApprovedEvent.Item item,
                          LocalDate effectiveDate) {
        UUID memberId = item.getMemberId();
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null) {
            log.warn("[PersonnelOrder] member not found - skipped. memberId={}", memberId);
            return;
        }
        UUID companyId = event.getCompanyId();
        boolean isFuture = effectiveDate.isAfter(LocalDate.now());

        //    미래 발령이면 appliedYn='N' 으로 저장, 즉시 발령이면 적용 후 'Y'
        PersonnelOrderType orderType = parseOrderType(item.getOrderType());
        PersonnelOrder order = PersonnelOrder.builder()
                .memberId(memberId)
                .companyId(companyId)
                .orderType(orderType)
                .effectiveDate(effectiveDate)
                .approvalDocumentId(event.getApprovalDocumentId())
                .beforeOrganizationId(item.getBeforeOrganizationId())
                .afterOrganizationId(item.getAfterOrganizationId())
                .beforeOrganizationName(item.getBeforeOrganizationName())
                .afterOrganizationName(item.getAfterOrganizationName())
                .beforeJobGradeName(item.getBeforeJobGradeName())
                .afterJobGradeName(item.getAfterJobGradeName())
                .beforeJobTitleName(item.getBeforeJobTitleName())
                .afterJobTitleName(item.getAfterJobTitleName())
                .beforeStep(item.getBeforeStep())
                .afterStep(item.getAfterStep())
                .reason(item.getReason())
                .approverId(event.getApproverId())
                .appliedYn("N")
                .build();
        personnelOrderRepository.save(order);

        if (isFuture) {
            log.info("[PersonnelOrder] scheduled - memberId={} effectiveDate={} (배치가 효력일에 적용)",
                    memberId, effectiveDate);
            return;
        }

        // 2) 효력일 도래 - 즉시 MemberPosition 반영
        applyToMemberPosition(order, item);
    }

    /**
     * 즉시 발령 또는 배치 호출 진입점
     */
    @Transactional
    public void applyToMemberPosition(PersonnelOrder order, PersonnelOrderApprovedEvent.Item itemHint) {
        UUID memberId = order.getMemberId();
        UUID companyId = order.getCompanyId();
        MemberPosition mp = memberPositionRepository
                .findActivePositionsByMemberIds(java.util.Set.of(memberId))
                .stream().findFirst().orElse(null);
        if (mp == null) {
            log.warn("[PersonnelOrder] active member position not found - skipped. memberId={}", memberId);
            return;
        }

        UUID afterOrgId = order.getAfterOrganizationId();
        Organization newOrg = afterOrgId != null
                ? organizationRepository.findById(afterOrgId).orElse(mp.getOrganization())
                : mp.getOrganization();
        JobGrade newGrade = order.getAfterJobGradeName() != null
                ? findJobGradeByName(companyId, order.getAfterJobGradeName()).orElse(mp.getJobGrade())
                : mp.getJobGrade();

        // 직책 처리 분기
        JobTitle newTitle;
        String afterTitleName = order.getAfterJobTitleName();
        if (afterTitleName == null) {
            newTitle = mp.getJobTitle();
        } else if (afterTitleName.isBlank()) {
            newTitle = null;
        } else {
            newTitle = findJobTitleByName(companyId, afterTitleName).orElse(mp.getJobTitle());
        }

        mp.update(newOrg, newGrade, newTitle, mp.getRole());
        order.markApplied();
        log.info("[PersonnelOrder] applied - memberId={} type={} effectiveDate={} orgAfter={} gradeAfter={} titleAfter={}",
                memberId, order.getOrderType(), order.getEffectiveDate(),
                order.getAfterOrganizationName(),
                order.getAfterJobGradeName(),
                order.getAfterJobTitleName());

        // 직급/직책/호봉 변경된 경우
        boolean gradeChanged = order.getAfterJobGradeName() != null
                && !order.getAfterJobGradeName().isBlank();
        boolean titleChanged = order.getAfterJobTitleName() != null;
        boolean stepChanged = order.getAfterStep() != null;
        if (gradeChanged || titleChanged || stepChanged) {
            try {
                salaryServiceClient.applyPersonnelOrder(
                        memberId,
                        companyId,
                        gradeChanged ? newGrade.getName() : null,
                        titleChanged && newTitle != null ? newTitle.getName() : null,
                        order.getAfterStep());
            } catch (Exception e) {
                log.warn("[PersonnelOrder] salary 동기화 실패 - memberId={} - {}", memberId, e.getMessage());
            }
        }
    }

    /**
     * 미래 발령 자동 적용 - 매일 새벽 배치 호출
     */
    @Transactional
    public int applyDuePendingOrders() {
        LocalDate today = LocalDate.now();
        List<PersonnelOrder> due = personnelOrderRepository
                .findByAppliedYnAndEffectiveDateLessThanEqual("N", today);
        int success = 0;
        for (PersonnelOrder order : due) {
            try {
                applyToMemberPosition(order, null);
                success++;
            } catch (Exception e) {
                log.error("[PersonnelOrder] batch apply failed - personnelOrderId={} - {}",
                        order.getPersonnelOrderId(), e.getMessage(), e);
            }
        }
        if (!due.isEmpty()) {
            log.info("[PersonnelOrder] 배치 적용 완료 - 대상 {} 건 / 성공 {} 건", due.size(), success);
        }
        return success;
    }

    private PersonnelOrderType parseOrderType(String s) {
        if (s == null) return PersonnelOrderType.ROLE_CHANGE;
        try {
            return PersonnelOrderType.valueOf(s);
        } catch (IllegalArgumentException e) {
            return PersonnelOrderType.ROLE_CHANGE;
        }
    }

    private Optional<JobGrade> findJobGradeByName(UUID companyId, String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        List<JobGrade> list = jobGradeRepository
                .findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(companyId, "NO");
        return list.stream().filter(g -> name.equals(g.getName())).findFirst();
    }

    private Optional<JobTitle> findJobTitleByName(UUID companyId, String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return jobTitleRepository
                .findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(companyId, "NO")
                .stream()
                .filter(t -> name.equals(t.getName()))
                .findFirst();
    }
}
