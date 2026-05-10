package com._team._team.organization.service;

import com._team._team.dto.BusinessException;
import com._team._team.event.OrganizationChangedEvent;
import com._team._team.event.OrganizationDeletedEvent;
import com._team._team.event.OrganizationSavedEvent;
import com._team._team.member.domain.Member;
import com._team._team.member.domain.MemberPosition;
import com._team._team.member.domain.SearchOutboxEvent;
import com._team._team.member.repository.MemberPositionRepository;
import com._team._team.member.repository.MemberRepository;
import com._team._team.member.repository.SearchOutboxEventRepository;
import com._team._team.organization.dto.reqdto.JobGradeReqDto;
import com._team._team.organization.dto.reqdto.JobTitleReqDto;
import com._team._team.organization.dto.reqdto.OrganizationReqDto;
import com._team._team.organization.dto.resdto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com._team._team.company.domain.Company;
import com._team._team.organization.domain.JobGrade;
import com._team._team.organization.domain.JobTitle;
import com._team._team.organization.domain.Organization;
import com._team._team.organization.repository.JobGradeRepository;
import com._team._team.organization.repository.JobTitleRepository;
import com._team._team.organization.repository.OrganizationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final JobGradeRepository jobGradeRepository;
    private final JobTitleRepository jobTitleRepository;
    private final MemberRepository memberRepository;
    private final MemberPositionRepository memberPositionRepository;
    private final SearchOutboxEventRepository searchOutboxEventRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public OrganizationService(OrganizationRepository organizationRepository, JobGradeRepository jobGradeRepository, JobTitleRepository jobTitleRepository, MemberRepository memberRepository, MemberPositionRepository memberPositionRepository, SearchOutboxEventRepository searchOutboxEventRepository, ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher) {
        this.organizationRepository = organizationRepository;
        this.jobGradeRepository = jobGradeRepository;
        this.jobTitleRepository = jobTitleRepository;
        this.memberRepository = memberRepository;
        this.memberPositionRepository = memberPositionRepository;
        this.searchOutboxEventRepository = searchOutboxEventRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    // 온보딩 시 기본 조직 생성
    public Organization createDefaultOrganization(Company company) {
        Organization organization = Organization.builder()
                .company(company)
                .name(company.getCompanyName())
                .displayOrder(0)
                .build();
        return organizationRepository.save(organization);
    }

    // 온보딩 시 기본 직급 생성
    public void createDefaultJobGrade(Company company) {
        String[] defaultGrades = {
                "관리자", "임원", "부장", "차장", "과장", "대리", "주임", "사원"
        };

        for (int i = 0; i < defaultGrades.length; i++) {
            JobGrade jobGrade = JobGrade.builder()
                    .company(company)
                    .name(defaultGrades[i])
                    .displayOrder(i)
                    .build();
            jobGradeRepository.save(jobGrade);
        }
    }

    // 온보딩 시 기본 직책 생성
    public void createDefaultJobTitle(Company company) {
        String[] defaultTitles = {
                "관리자", "CEO", "본부장", "실장", "팀장", "파트장", "팀원"
        };

        for (int i = 0; i < defaultTitles.length; i++) {
            JobTitle jobTitle = JobTitle.builder()
                    .company(company)
                    .name(defaultTitles[i])
                    .displayOrder(i)
                    .build();
            jobTitleRepository.save(jobTitle);
        }
    }

    // 조직 생성
    public UUID createOrganization(UUID memberId, OrganizationReqDto reqDto) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));
        Company company = member.getCompany();

        Organization parent = null;
        if (reqDto.getParentId() != null) {
            parent = organizationRepository.findById(reqDto.getParentId())
                    .orElseThrow(() -> new BusinessException(
                            HttpStatus.NOT_FOUND, "존재하지 않는 상위 조직입니다."
                    ));
            if (!parent.getCompany().getCompanyId().equals(company.getCompanyId())) {
                throw new BusinessException(
                        HttpStatus.FORBIDDEN, "다른 회사의 조직입니다."
                );
            }
        }

        int displayOrder = organizationRepository
                .findByCompanyAndDelYnOrderByDisplayOrderWithLock(company, "NO").size();

        Organization organization = Organization.builder()
                .company(company)
                .name(reqDto.getName())
                .parent(parent)
                .displayOrder(displayOrder)
                .build();

        Organization saved = organizationRepository.save(organization); // ← 한번만 저장

        eventPublisher.publishEvent(
                new OrganizationChangedEvent(saved.getOrganizationId()));

        return saved.getOrganizationId();
    }


    // 조직 목록 조회 (트리 구조)
    @Transactional(readOnly = true)
    public List<OrganizationResDto> getOrganizationList(UUID memberId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        return organizationRepository
                .findByCompanyWithChildren(member.getCompany(), "NO")
                .stream()
                .filter(o -> o.getParent() == null)
                .map(OrganizationResDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 조직 수정
    public void updateOrganization(UUID memberId, UUID organizationId,
                                   OrganizationReqDto reqDto) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 조직입니다."
                ));

        if (!organization.getCompany().getCompanyId()
                .equals(member.getCompany().getCompanyId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "다른 회사의 조직입니다.");
        }

        organization.updateName(reqDto.getName());

        eventPublisher.publishEvent(
                new OrganizationChangedEvent(organizationId));
    }

    // 조직 삭제
    public void deleteOrganization(UUID memberId, UUID organizationId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 조직입니다."
                ));

        if (!organization.getCompany().getCompanyId()
                .equals(member.getCompany().getCompanyId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "다른 회사의 조직입니다.");
        }

        if (!organization.getChildren().isEmpty()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "하위 조직이 있어 삭제할 수 없습니다."
            );
        }
        if (memberPositionRepository.existsByOrganizationAndDelYn(organization, "NO")) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "해당 조직에 속한 직원이 있어 삭제할 수 없습니다."
            );
        }

        organization.delete();

        eventPublisher.publishEvent(
                OrganizationChangedEvent.deleted(organizationId));
    }
    // 직급 생성
    public UUID createJobGrade(UUID memberId, JobGradeReqDto reqDto) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."));
        Company company = member.getCompany();

        // 동일 순서 중복 체크
        boolean exists = jobGradeRepository
                .existsByCompany_CompanyIdAndDisplayOrderAndDelYn(
                        company.getCompanyId(), reqDto.getDisplayOrder(), "NO");
        if (exists) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "이미 사용 중인 순서입니다.");
        }

        JobGrade jobGrade = JobGrade.builder()
                .company(company)
                .name(reqDto.getName())
                .displayOrder(reqDto.getDisplayOrder())
                .build();

        return jobGradeRepository.save(jobGrade).getJobGradeId();
    }

    // 직급 목록 조회
    @Transactional(readOnly = true)
    public List<JobGradeResDto> getJobGradeList(UUID memberId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        return jobGradeRepository
                .findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(
                        member.getCompany().getCompanyId(), "NO")
                .stream()
                .map(JobGradeResDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 회사별 직급 목록
    @Transactional(readOnly = true)
    public List<JobGradeResDto> getJobGradeListByCompany(UUID companyId) {
        return jobGradeRepository
                .findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(companyId, "NO")
                .stream()
                .map(JobGradeResDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 직급 수정
    public void updateJobGrade(UUID memberId, UUID jobGradeId, JobGradeReqDto reqDto) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        JobGrade jobGrade = jobGradeRepository.findById(jobGradeId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 직급입니다."
                ));

        if (!jobGrade.getCompany().getCompanyId()
                .equals(member.getCompany().getCompanyId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "다른 회사의 직급입니다.");
        }

        jobGrade.updateName(reqDto.getName());
        jobGrade.updateDisplayOrder(reqDto.getDisplayOrder());
    }

    // 직급 삭제
    public void deleteJobGrade(UUID memberId, UUID jobGradeId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        JobGrade jobGrade = jobGradeRepository.findById(jobGradeId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 직급입니다."
                ));

        if (!jobGrade.getCompany().getCompanyId()
                .equals(member.getCompany().getCompanyId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "다른 회사의 직급입니다.");
        }

        // 사용 중인 직원 있는지 확인
        if (memberPositionRepository.existsByJobGradeAndDelYn(jobGrade, "NO")) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "사용 중인 직원이 있어 삭제할 수 없습니다."
            );
        }

        jobGrade.delete();
    }

    public void reorderJobGrade(UUID memberId, List<UUID> jobGradeIdList) {

        if (jobGradeIdList == null || jobGradeIdList.isEmpty()) return;

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));
        UUID companyId = member.getCompany().getCompanyId();

        List<JobGrade> jobGrades = jobGradeRepository.findAllById(jobGradeIdList);
        if (jobGrades.size() != jobGradeIdList.size()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 직급이 포함되어 있습니다.");
        }

        for (JobGrade jobGrade : jobGrades) {
            if (!jobGrade.getCompany().getCompanyId().equals(companyId)) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "다른 회사의 직급입니다.");
            }
            if (!"NO".equals(jobGrade.getDelYn())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "삭제된 직급은 순서를 변경할 수 없습니다.");
            }
        }

        for (int i = 0; i < jobGradeIdList.size(); i++) {
            UUID id = jobGradeIdList.get(i);
            int order = i;
            jobGrades.stream()
                    .filter(jobGrade -> jobGrade.getJobGradeId().equals(id))
                    .findFirst()
                    .ifPresent(jobGrade -> jobGrade.updateDisplayOrder(order));
        }

        jobGradeRepository.saveAll(jobGrades);
    }

    // 직책 생성
    public UUID createJobTitle(UUID memberId, JobTitleReqDto reqDto) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."));
        Company company = member.getCompany();

        // 동일 순서 중복 체크
        boolean exists = jobTitleRepository
                .existsByCompany_CompanyIdAndDisplayOrderAndDelYn(
                        company.getCompanyId(), reqDto.getDisplayOrder(), "NO");
        if (exists) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "이미 사용 중인 순서입니다.");
        }

        JobTitle jobTitle = JobTitle.builder()
                .company(company)
                .name(reqDto.getName())
                .displayOrder(reqDto.getDisplayOrder())
                .build();

        return jobTitleRepository.save(jobTitle).getJobTitleId();
    }

    // 직책 목록 조회
    @Transactional(readOnly = true)
    public List<JobTitleResDto> getJobTitleList(UUID memberId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        return jobTitleRepository
                .findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(
                        member.getCompany().getCompanyId(), "NO")
                .stream()
                .map(JobTitleResDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 직책 수정
    public void updateJobTitle(UUID memberId, UUID jobTitleId, JobTitleReqDto reqDto) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        JobTitle jobTitle = jobTitleRepository.findById(jobTitleId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 직책입니다."
                ));

        if (!jobTitle.getCompany().getCompanyId()
                .equals(member.getCompany().getCompanyId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "다른 회사의 직책입니다.");
        }

        jobTitle.updateName(reqDto.getName());
        jobTitle.updateDisplayOrder(reqDto.getDisplayOrder());
    }

    // 직책 삭제
    public void deleteJobTitle(UUID memberId, UUID jobTitleId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        JobTitle jobTitle = jobTitleRepository.findById(jobTitleId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 직책입니다."
                ));

        if (!jobTitle.getCompany().getCompanyId()
                .equals(member.getCompany().getCompanyId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "다른 회사의 직책입니다.");
        }

        // 사용 중인 직원 있는지 확인
        if (memberPositionRepository.existsByJobTitleAndDelYn(jobTitle, "NO")) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "사용 중인 직원이 있어 삭제할 수 없습니다."
            );
        }

        jobTitle.delete();
    }
    public void reorderJobTitle(UUID memberId, List<UUID> jobTitleIdList) {

        if (jobTitleIdList == null || jobTitleIdList.isEmpty()) return;

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));
        UUID companyId = member.getCompany().getCompanyId();

        List<JobTitle> jobTitles = jobTitleRepository.findAllById(jobTitleIdList);
        if (jobTitles.size() != jobTitleIdList.size()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 직책이 포함되어 있습니다.");
        }

        for (JobTitle jobTitle : jobTitles) {
            if (!jobTitle.getCompany().getCompanyId().equals(companyId)) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "다른 회사의 직책입니다.");
            }
            if (!"NO".equals(jobTitle.getDelYn())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "삭제된 직책은 순서를 변경할 수 없습니다.");
            }
        }

        for (int i = 0; i < jobTitleIdList.size(); i++) {
            UUID id = jobTitleIdList.get(i);
            int order = i;
            jobTitles.stream()
                    .filter(jobTitle -> jobTitle.getJobTitleId().equals(id))
                    .findFirst()
                    .ifPresent(jobTitle -> jobTitle.updateDisplayOrder(order));
        }

        jobTitleRepository.saveAll(jobTitles);
    }
    public void reorderOrganization(List<UUID> organizationIdList) {

        if (organizationIdList == null || organizationIdList.isEmpty()) return;

        List<Organization> organizations = organizationRepository
                .findAllById(organizationIdList);

        if (organizations.size() != organizationIdList.size()) {
            throw new BusinessException(
                    HttpStatus.NOT_FOUND, "일부 조직을 찾을 수 없습니다."
            );
        }

        // 모든 조직이 같은 부모인지 확인
        UUID firstParentId = organizations.get(0).getParent() != null ?
                organizations.get(0).getParent().getOrganizationId() : null;

        for (Organization org : organizations) {
            UUID currentParentId = org.getParent() != null ?
                    org.getParent().getOrganizationId() : null;
            if (!Objects.equals(firstParentId, currentParentId)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "다른 레벨의 조직을 함께 변경할 수 없습니다."
                );
            }
        }

        // displayOrder 업데이트
        for (int i = 0; i < organizationIdList.size(); i++) {
            UUID id = organizationIdList.get(i);
            int order = i;
            organizations.stream()
                    .filter(o -> o.getOrganizationId().equals(id))
                    .findFirst()
                    .ifPresent(o -> o.updateDisplayOrder(order));
        }

        organizationRepository.saveAll(organizations);
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void handleOrganizationEvent(OrganizationChangedEvent event) {
        if (event.isDeleted()) {
            saveOrganizationDeletedOutboxEvent(event.getOrganizationId());
        } else {
            saveOrganizationSavedOutboxEvent(event.getOrganizationId());
        }
    }
    public void saveOrganizationSavedOutboxEvent(UUID organizationId) {
        try {
            Organization organization = organizationRepository
                    .findById(organizationId)
                    .orElseThrow();

            OrganizationSavedEvent savedEvent = OrganizationSavedEvent.builder()
                    .organizationId(organizationId)
                    .name(organization.getName())
                    .parentId(organization.getParent() != null
                            ? organization.getParent().getOrganizationId()
                            : null)
                    .companyId(organization.getCompany().getCompanyId())
                    .displayOrder(organization.getDisplayOrder())
                    .build();

            String payload = objectMapper.writeValueAsString(savedEvent);

            SearchOutboxEvent outboxEvent = SearchOutboxEvent.builder()
                    .topic("organization-saved")
                    .aggregateId(organizationId)
                    .payload(payload)
                    .processed("NO")
                    .createdAt(LocalDateTime.now())
                    .build();

            searchOutboxEventRepository.save(outboxEvent);

        } catch (Exception e) {
            log.error("OrganizationSavedOutboxEvent 저장 실패: {}",
                    e.getMessage());
        }
    }
    public void saveOrganizationDeletedOutboxEvent(UUID organizationId) {
        try {
            // 조직 조회 (이미 삭제됐지만 delYn=YES 상태로 조회 가능)
            Organization organization = organizationRepository
                    .findById(organizationId)
                    .orElseThrow();

            OrganizationDeletedEvent deletedEvent =
                    OrganizationDeletedEvent.builder()
                            .organizationId(organizationId)
                            .companyId(organization.getCompany().getCompanyId())
                            .build();

            String payload = objectMapper.writeValueAsString(deletedEvent);

            SearchOutboxEvent outboxEvent = SearchOutboxEvent.builder()
                    .topic("organization-deleted")
                    .aggregateId(organizationId)
                    .payload(payload)
                    .processed("NO")
                    .createdAt(LocalDateTime.now())
                    .build();

            searchOutboxEventRepository.save(outboxEvent);

        } catch (Exception e) {
            log.error("OrganizationDeletedOutboxEvent 저장 실패: {}",
                    e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public OrgChartWrapperResDto getOrgChart(UUID memberId) {
        Member member = getMember(memberId);
        Company company = member.getCompany();

        List<Organization> allOrgs = organizationRepository
                .findByCompanyAndDelYnOrderByDisplayOrder(company, "NO");

        List<OrgChartResDto> orgTree = buildOrgTree(allOrgs, null);

        return OrgChartWrapperResDto.builder()
                .companyName(company.getCompanyName())
                .organizations(orgTree)
                .build();
    }

    private List<OrgChartResDto> buildOrgTree(
            List<Organization> allOrgs,
            UUID parentId) {

        return allOrgs.stream()
                .filter(org -> parentId == null
                        ? org.getParent() == null
                        : org.getParent() != null
                        && org.getParent().getOrganizationId().equals(parentId))
                .map(org -> {
                    // 해당 조직 직원 조회
                    List<MemberPosition> positions = memberPositionRepository
                            .findByOrganizationAndDelYn(org, "NO");

                    // 직원 목록 (직급 displayOrder 기준 정렬)
                    List<OrgChartMemberResDto> members = positions.stream()
                            .filter(p -> "NO".equals(p.getMember().getDelYn()))
                            .sorted(Comparator.comparingInt(
                                    p -> p.getJobGrade().getDisplayOrder()))
                            .map(p -> OrgChartMemberResDto.fromEntity(p.getMember(), p))
                            .collect(Collectors.toList());

                    // 재귀로 자식 조직 조회
                    List<OrgChartResDto> children = buildOrgTree(
                            allOrgs,
                            org.getOrganizationId());

                    return OrgChartResDto.builder()
                            .organizationId(org.getOrganizationId())
                            .name(org.getName())
                            .members(members)
                            .children(children)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private Member getMember(UUID memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."));
    }

    public OrganizationResDto findById(UUID organizationId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException("조직을 찾을 수 없습니다."));
        return OrganizationResDto.fromEntity(organization);
    }

    /**
     * 주어진 조직 및 모든 하위 조직 ID 목록 반환 (재귀)
     * - 루트 자신 포함
     * - 삭제된(delYn != "NO") 조직은 제외 (하위 트리도 함께 잘림)
     * - approval-service 부서 문서함에서 사용
     */
    @Transactional(readOnly = true)
    public List<UUID> findDescendantIds(UUID organizationId) {
        Organization root = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 조직입니다."));

        List<UUID> result = new ArrayList<>();
        collectDescendantIds(root, result);
        return result;
    }

    private void collectDescendantIds(Organization organization, List<UUID> result) {
        // 삭제된 조직은 제외하고, 해당 서브트리도 더 내려가지 않음
        if (!"NO".equals(organization.getDelYn())) {
            return;
        }
        result.add(organization.getOrganizationId());
        for (Organization child : organization.getChildren()) {
            collectDescendantIds(child, result);
        }
    }
}
