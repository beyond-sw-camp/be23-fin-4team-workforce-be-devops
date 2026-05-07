package com._team._team.demo;

import com._team._team.company.domain.Company;
import com._team._team.company.dto.reqdto.CompanyOnboardingReqDto;
import com._team._team.company.service.CompanyService;
import com._team._team.member.domain.*;
import com._team._team.member.domain.enums.*;
import com._team._team.member.dto.reqdto.MemberCreateReqDto;
import com._team._team.member.repository.*;
import com._team._team.member.service.MemberService;
import com._team._team.organization.domain.JobGrade;
import com._team._team.organization.domain.JobTitle;
import com._team._team.organization.domain.Organization;
import com._team._team.organization.dto.reqdto.OrganizationReqDto;
import com._team._team.organization.repository.JobGradeRepository;
import com._team._team.organization.repository.JobTitleRepository;
import com._team._team.organization.repository.OrganizationRepository;
import com._team._team.organization.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoCreateTestData implements ApplicationRunner {

    private final MemberRepository memberRepository;
    private final CompanyService companyService;
    private final MemberService memberService;
    private final OrganizationService organizationService;
    private final OrganizationRepository organizationRepository;
    private final JobGradeRepository jobGradeRepository;
    private final JobTitleRepository jobTitleRepository;
    private final RoleRepository roleRepository;
    private final MemberPositionRepository memberPositionRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 데모 시드 토글 - 운영 배포 시 false 유지 - >  말 그대로 데모 데이터 -> 온갖 테스트용
     * true 일 때만 createDemoCompanies() 호출 -> 회사 4 (당월/연봉제), 회사 5 (전월/호봉제) 생성.
     * salary-service 의 DemoSalarySeedRunner 가 동일 toggle 로 시드 데이터 보강
     */
    @org.springframework.beans.factory.annotation.Value("${seed.demo.enabled:false}")
    private boolean seedDemoEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 기본 회사 (1~3): DB 비어있을 때만 시드
        if (memberRepository.count() == 0) {
            log.info("[SEED] 기본 회사 1~3 시드 시작");
            createBaseCompanies();
        } else {
            log.info("[SEED] 기존 데이터 있음 - 기본 회사 시드 skip");
        }

        // 데모 회사 (4, 5): toggle ON + admin4 미존재 시에만 시드 (기존 데이터 보존)
        if (seedDemoEnabled
                && memberRepository.findByEmail("admin4@workforce.com").isEmpty()) {
            log.info("[DEMO] seed.demo.enabled=true + admin4 미존재 - 데모 회사 시드 시작");
            createDemoCompanies();
        } else {
            log.info("[DEMO] 데모 회사 시드 skip (toggle={} / admin4 존재 가능)", seedDemoEnabled);
        }
    }

    /**
     * 데모 회사 셋업 - 24개월 풀 데이터 (Salary/Payroll/Ledger) 시드용
     * salary-service 의 DemoSalarySeedRunner 가 회사명 prefix "데모-" 로 식별해서 데이터 시드
     * - 회사 4: 데모 - 당월·연봉제 (CURRENT_MONTH, payDay=25)
     * - 회사 5: 데모 - 전월·호봉제 (PREVIOUS_MONTH, payDay=10, usePayGrade=Y)
     */
    private void createDemoCompanies() {
        String[] companyNames = { "데모 - 당월/연봉제", "데모 - 전월/호봉제" };
        String[] companyDomains = { "demo-current", "demo-prev" };
        for (int idx = 0; idx < companyNames.length; idx++) {
            int adminSeq = 4 + idx; // admin 4 / admin 5
            String companyName = companyNames[idx];
            String adminEmail = "admin" + adminSeq + "@workforce.com";

            companyService.onboarding(
                    CompanyOnboardingReqDto.builder()
                            .companyName(companyName)
                            .companyDomain(companyDomains[idx])
                            .ceoName("김대표" + adminSeq)
                            .businessNumber("12345678" + adminSeq + adminSeq)
                            .address("서울시 강남구 테헤란로 " + adminSeq)
                            .detailAddress(adminSeq + "층")
                            .adminName("관리자" + adminSeq)
                            .adminEmail(adminEmail)
                            .adminPassword("test1234!")
                            .adminPasswordCheck("test1234!")
                            .build());

            Member admin = memberRepository.findByEmail(adminEmail).orElseThrow();
            Company company = admin.getCompany();
            UUID adminId = admin.getMemberId();

            Organization topOrg = organizationRepository
                    .findByCompany_CompanyIdAndDelYn(company.getCompanyId(), "NO")
                    .stream().findFirst().orElseThrow();

            UUID hrTeamId = organizationService.createOrganization(adminId,
                    OrganizationReqDto.builder().name("인사팀").parentId(topOrg.getOrganizationId()).build());
            UUID devTeamId = organizationService.createOrganization(adminId,
                    OrganizationReqDto.builder().name("개발팀").parentId(topOrg.getOrganizationId()).build());

            JobGrade seniorGrade = jobGradeRepository.save(
                    JobGrade.builder().company(company).name("과장").displayOrder(1).delYn("NO").build());
            JobGrade juniorGrade = jobGradeRepository.save(
                    JobGrade.builder().company(company).name("사원").displayOrder(2).delYn("NO").build());

            JobTitle teamLeaderTitle = jobTitleRepository.save(
                    JobTitle.builder().company(company).name("팀장").displayOrder(1).delYn("NO").build());
            JobTitle memberTitle = jobTitleRepository.save(
                    JobTitle.builder().company(company).name("팀원").displayOrder(2).delYn("NO").build());

            List<Role> roles = roleRepository
                    .findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(company.getCompanyId(), "NO");
            Role teamLeaderRole = roles.stream().filter(r -> r.getName().equals("팀장")).findFirst().orElseThrow();
            Role employeeRole = roles.stream().filter(r -> r.getName().equals("일반 직원")).findFirst().orElseThrow();

            // 직원 6명 - 입사일 24개월 분산 (장기근속자 + 신규자 혼합)
            //   장기근속 2명 (24개월/18개월 전) - 퇴직금 시뮬 1년 이상 케이스
            //   중기 2명 (12개월/9개월 전)
            //   단기 2명 (6개월/3개월 전) - 1년 미만 퇴직금 케이스
            String[] demoNames = { "최장수", "오근속", "박중간", "김중기", "신단기", "유신규" };
            String[] demoInitials = { "CJS", "OGS", "PJG", "KJG", "SDG", "YSG" };
            LocalDate today = LocalDate.now();
            List<LocalDate> demoJoinDates = Arrays.asList(
                    today.minusMonths(24),
                    today.minusMonths(18),
                    today.minusMonths(12),
                    today.minusMonths(9),
                    today.minusMonths(6),
                    today.minusMonths(3)
            );

            for (int i = 0; i < demoNames.length; i++) {
                UUID teamId = (i < 3) ? hrTeamId : devTeamId;
                UUID jobGradeId = (i < 2) ? seniorGrade.getJobGradeId() : juniorGrade.getJobGradeId();
                UUID jobTitleId = (i == 0 || i == 3) ? teamLeaderTitle.getJobTitleId() : memberTitle.getJobTitleId();
                UUID roleId = (i == 0 || i == 3) ? teamLeaderRole.getRoleId() : employeeRole.getRoleId();
                String email = "emp_demo" + adminSeq + "_" + (i + 1) + "@gmail.com";

                memberService.createMember(adminId, admin.getDefaultPositionId(),
                        MemberCreateReqDto.builder()
                                .name(demoNames[i] + adminSeq)
                                .englishInitial(demoInitials[i])
                                .personalEmail(email)
                                .joinDate(demoJoinDates.get(i))
                                .employmentType(EmploymentType.FULL_TIME)
                                .organizationId(teamId)
                                .jobGradeId(jobGradeId)
                                .jobTitleId(jobTitleId)
                                .roleId(roleId)
                                .build());

                Member created = memberRepository.findByPersonalEmail(email).orElseThrow();
                created.updatePassword(passwordEncoder.encode("Test1234!"));
                created.completeFirstLogin();
            }

            log.info("[DEMO] 회사 셋업 완료: {} (admin={}, 직원 {}명)",
                    companyName, adminEmail, demoNames.length);
        }
    }

    private void createBaseCompanies() {
        for (int c = 1; c <= 3; c++) {

            // 1. 회사 온보딩
            companyService.onboarding(
                    CompanyOnboardingReqDto.builder()
                            .companyName("워크포스 컴퍼니 " + c)
                            .companyDomain("workforce" + c)
                            .ceoName("김대표" + c)
                            .businessNumber("123456789" + c)
                            .address("서울시 강남구 테헤란로 " + c)
                            .detailAddress(c + "층")
                            .adminName("관리자" + c)
                            .adminEmail("admin" + c + "@workforce.com")
                            .adminPassword("test1234!")
                            .adminPasswordCheck("test1234!")
                            .build());

            // 2. 관리자 조회
            Member admin = memberRepository
                    .findByEmail("admin" + c + "@workforce.com")
                    .orElseThrow();

            Company company = admin.getCompany();
            UUID adminId = admin.getMemberId();

            // 3. 최상위 조직 조회
            Organization topOrg = organizationRepository
                    .findByCompany_CompanyIdAndDelYn(
                            company.getCompanyId(), "NO")
                    .stream()
                    .findFirst()
                    .orElseThrow();

            // 4. 하위 조직 생성
            UUID hrTeamId = organizationService.createOrganization(
                    adminId,
                    OrganizationReqDto.builder()
                            .name("인사팀")
                            .parentId(topOrg.getOrganizationId())
                            .build());

            UUID devTeamId = organizationService.createOrganization(
                    adminId,
                    OrganizationReqDto.builder()
                            .name("개발팀")
                            .parentId(topOrg.getOrganizationId())
                            .build());

            // 5. 직급 생성
            JobGrade seniorGrade = jobGradeRepository.save(
                    JobGrade.builder()
                            .company(company)
                            .name("과장")
                            .displayOrder(1)
                            .delYn("NO")
                            .build());

            JobGrade juniorGrade = jobGradeRepository.save(
                    JobGrade.builder()
                            .company(company)
                            .name("사원")
                            .displayOrder(2)
                            .delYn("NO")
                            .build());

            // 6. 직책 생성
            JobTitle teamLeaderTitle = jobTitleRepository.save(
                    JobTitle.builder()
                            .company(company)
                            .name("팀장")
                            .displayOrder(1)
                            .delYn("NO")
                            .build());

            JobTitle memberTitle = jobTitleRepository.save(
                    JobTitle.builder()
                            .company(company)
                            .name("팀원")
                            .displayOrder(2)
                            .delYn("NO")
                            .build());

            // 7. 역할 조회 (온보딩 시 생성된 기본 역할)
            List<Role> roles = roleRepository
                    .findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(
                            company.getCompanyId(), "NO");

            Role teamLeaderRole = roles.stream()
                    .filter(r -> r.getName().equals("팀장"))
                    .findFirst()
                    .orElseThrow();

            Role employeeRole = roles.stream()
                    .filter(r -> r.getName().equals("일반 직원"))
                    .findFirst()
                    .orElseThrow();

            // 8. 직원 생성
            List<String> names = Arrays.asList(
                    "박세민", "이지연", "이다은",
                    "김정훈", "김도균",
                    "황주안", "이병찬", "홍진희",
                    "박준형", "정명진"
            );

            List<String> initials = Arrays.asList(
                    "KIS", "PIS", "LGH",
                    "JMK", "CYB",
                    "HBE", "MFE", "SDS",
                    "HDT", "OIF"
            );

            List<LocalDate> joinDates = Arrays.asList(
                    LocalDate.of(2025, 1, 2),
                    LocalDate.of(2025, 2, 3),
                    LocalDate.of(2025, 3, 4),
                    LocalDate.now().minusYears(3),
                    LocalDate.now().minusYears(5),
                    LocalDate.now().minusYears(2),
                    LocalDate.now().minusMonths(20),
                    LocalDate.of(2025, 4, 1),
                    LocalDate.of(2025, 5, 1),
                    LocalDate.of(2025, 6, 1)
            );

            List<EmploymentType> employmentTypes = Arrays.asList(
                    EmploymentType.FULL_TIME,
                    EmploymentType.FULL_TIME,
                    EmploymentType.CONTRACT,
                    EmploymentType.FULL_TIME,
                    EmploymentType.FULL_TIME,
                    EmploymentType.FULL_TIME,
                    EmploymentType.INTERN,
                    EmploymentType.FULL_TIME,
                    EmploymentType.CONTRACT,
                    EmploymentType.FULL_TIME
            );

            for (int i = 0; i < names.size(); i++) {
                UUID teamId = (i < 5) ? hrTeamId : devTeamId;
                UUID jobGradeId = (i < 3)
                        ? seniorGrade.getJobGradeId()
                        : juniorGrade.getJobGradeId();
                UUID jobTitleId = (i == 0 || i == 5)
                        ? teamLeaderTitle.getJobTitleId()
                        : memberTitle.getJobTitleId();
                UUID roleId = (i == 0 || i == 5)
                        ? teamLeaderRole.getRoleId()
                        : employeeRole.getRoleId();

                memberService.createMember(
                        adminId,
                        admin.getDefaultPositionId(),
                        MemberCreateReqDto.builder()
                                .name(names.get(i) + c)
                                .englishInitial(initials.get(i))
                                .personalEmail("emp" + c + "_"
                                        + (i + 1) + "@gmail.com")
                                .joinDate(joinDates.get(i))
                                .employmentType(employmentTypes.get(i))
                                .organizationId(teamId)
                                .jobGradeId(jobGradeId)
                                .jobTitleId(jobTitleId)
                                .roleId(roleId)
                                .build());

                Member createdMember = memberRepository
                        .findByPersonalEmail(
                                "emp" + c + "_" + (i + 1) + "@gmail.com")
                        .orElseThrow();

                createdMember.updatePassword(
                        passwordEncoder.encode("Test1234!"));

                // 최초 로그인 여부 NO로 변경
                // (비밀번호 변경 없이 바로 로그인 가능하게)
                createdMember.completeFirstLogin();
            }

            log.info("테스트 데이터 생성 완료 - 회사: {}",
                    company.getCompanyName());
        }
    }
}