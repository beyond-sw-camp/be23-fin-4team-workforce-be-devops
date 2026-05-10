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
class AutoCreateTestData implements ApplicationRunner {

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

    // 데모 비밀번호 동일 - 1회만 해싱 후 모든 직원에 재사용
    private String cachedDemoPasswordHash;

    private String getDemoPasswordHash() {
        if (cachedDemoPasswordHash == null) {
            cachedDemoPasswordHash = passwordEncoder.encode("Test1234!");
        }
        return cachedDemoPasswordHash;
    }

    /**
     * 데모 시드 토글 - 운영 배포 시 false 유지
     * true 일 때만 createDemoCompanies() 호출 -> 회사 4 (당월/연봉제), 회사 5 (전월/호봉제) 생성
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
     * 데모 회사 셋업 - 36개월 풀 데이터 (Salary/Payroll/Ledger) 시드용
     */
    private void createDemoCompanies() {
        // 가상 IT 회사명
        String[] companyNames = {
                "(주)서울디지털테크",
                "(주)강남솔루션",
                "(주)판교크리에이션",
                "(주)역삼파트너스",
                "(주)상도동테크놀로지"
        };
        String[] companyDomains = {
                "demo-current", "demo-prev", "demo-3", "demo-4", "demo-5"
        };
        String[] ceoNames = { "박정훈", "이서연", "한도윤", "최예린", "김지환" };
        String[] adminNames = { "정인사", "김인사", "이인사", "박인사", "홍인사" };
        for (int idx = 0; idx < companyNames.length; idx++) {
            int adminSeq = 4 + idx; // admin 4 / admin 5
            String companyName = companyNames[idx];
            String adminEmail = "admin" + adminSeq + "@workforce.com";

            companyService.onboarding(
                    CompanyOnboardingReqDto.builder()
                            .companyName(companyName)
                            .companyDomain(companyDomains[idx])
                            .ceoName(ceoNames[idx])
                            .businessNumber("12345678" + adminSeq + adminSeq)
                            .address("서울시 강남구 테헤란로 " + (100 + adminSeq))
                            .detailAddress(adminSeq + "층")
                            .adminName(adminNames[idx])
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

            // 직급/직책은 OrganizationService default 시드에서 이미 생성
            List<JobGrade> grades = jobGradeRepository
                    .findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(company.getCompanyId(), "NO");
            JobGrade seniorGrade = grades.stream().filter(g -> "과장".equals(g.getName())).findFirst().orElseThrow();
            JobGrade juniorGrade = grades.stream().filter(g -> "사원".equals(g.getName())).findFirst().orElseThrow();

            List<JobTitle> titles = jobTitleRepository
                    .findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(company.getCompanyId(), "NO");
            JobTitle teamLeaderTitle = titles.stream().filter(t -> "팀장".equals(t.getName())).findFirst().orElseThrow();
            JobTitle memberTitle = titles.stream().filter(t -> "팀원".equals(t.getName())).findFirst().orElseThrow();

            List<Role> roles = roleRepository
                    .findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(company.getCompanyId(), "NO");
            Role hrManagerRole = roles.stream().filter(r -> r.getName().equals("인사 관리자")).findFirst().orElseThrow();
            Role teamLeaderRole = roles.stream().filter(r -> r.getName().equals("팀장")).findFirst().orElseThrow();
            Role employeeRole = roles.stream().filter(r -> r.getName().equals("일반 직원")).findFirst().orElseThrow();

            // 직원 25명 - 한국 가상 인명, 입사일 분산
            // 10년+ 5명, 5~9년 4명, 1~4년 11명, 1년 미만 5명
            // i=0,13 = 팀장 (인사팀장/개발팀장), i=0~4 = senior(과장), 나머지 junior(사원)
            String[] demoNames = {
                    "정수영", "한지호", "오미경", "강대훈", "윤재성",   // 10년+ (0~4)
                    "박서연", "이준혁", "최유진", "임도현",             // 5~9년 (5~8)
                    "조성훈", "황민지", "권시우", "송하늘", "안재현",
                    "노유리", "유태민", "백서영", "문지원", "신경수",
                    "오세린",                                          // 1~4년 (9~19)
                    "차민준", "곽지수", "표시현", "전규민", "주하은"   // 1년 미만 (20~24)
            };
            String[] demoInitials = {
                    "JSY", "HJH", "OMK", "KDH", "YJS",
                    "PSY", "LJH", "CYJ", "IDH",
                    "JSH", "HMJ", "KSW", "SHN", "AJH",
                    "NYR", "YTM", "BSY", "MJW", "SKS",
                    "OSL",
                    "CMJ", "GJS", "PSH", "JGM", "JHE"
            };
            LocalDate today = LocalDate.now();
            // 입사일 - 인덱스 i 별 분포 (장기근속자가 앞쪽 인덱스)
            int[] monthsAgo = {
                    156, 144, 132, 120, 121, // 10년+ (13/12/11/10/10년)
                    96, 84, 72, 60,           // 5~9년 (8/7/6/5년)
                    48, 42, 36, 30, 26, 22, 18, 15, 12, 10, 9,
                    6, 5, 4, 3, 2
            };

            for (int i = 0; i < demoNames.length; i++) {
                UUID teamId = (i < 13) ? hrTeamId : devTeamId;
                // 10년+ 5명은 senior(과장), 나머지 junior(사원)
                UUID jobGradeId = (i < 5) ? seniorGrade.getJobGradeId() : juniorGrade.getJobGradeId();
                // 팀장: 인사팀장 i=0, 개발팀장 i=13
                boolean isLead = (i == 0 || i == 13);
                boolean isHrLead = (i == 0); // 인사팀장은 인사 관리자 권한 (조직개편/구성원 관리 등)
                UUID jobTitleId = isLead ? teamLeaderTitle.getJobTitleId() : memberTitle.getJobTitleId();
                UUID roleId = isHrLead ? hrManagerRole.getRoleId()
                        : isLead ? teamLeaderRole.getRoleId()
                        : employeeRole.getRoleId();
                String email = "emp_demo" + adminSeq + "_" + (i + 1) + "@gmail.com";

                memberService.createMember(adminId, admin.getDefaultPositionId(),
                        MemberCreateReqDto.builder()
                                .name(demoNames[i])
                                .englishInitial(demoInitials[i])
                                .personalEmail(email)
                                .joinDate(today.minusMonths(monthsAgo[i]))
                                .employmentType(EmploymentType.FULL_TIME)
                                .organizationId(teamId)
                                .jobGradeId(jobGradeId)
                                .jobTitleId(jobTitleId)
                                .roleId(roleId)
                                .build());

                Member created = memberRepository.findByPersonalEmail(email).orElseThrow();
                created.updatePassword(getDemoPasswordHash());
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

            List<JobGrade> grades = jobGradeRepository
                    .findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(company.getCompanyId(), "NO");
            JobGrade seniorGrade = grades.stream().filter(g -> "과장".equals(g.getName())).findFirst().orElseThrow();
            JobGrade juniorGrade = grades.stream().filter(g -> "사원".equals(g.getName())).findFirst().orElseThrow();

            List<JobTitle> titles = jobTitleRepository
                    .findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(company.getCompanyId(), "NO");
            JobTitle teamLeaderTitle = titles.stream().filter(t -> "팀장".equals(t.getName())).findFirst().orElseThrow();
            JobTitle memberTitle = titles.stream().filter(t -> "팀원".equals(t.getName())).findFirst().orElseThrow();

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

                createdMember.updatePassword(getDemoPasswordHash());

                // 최초 로그인 여부 NO로 변경
                // (비밀번호 변경 없이 바로 로그인 가능하게)
                createdMember.completeFirstLogin();
            }

            log.info("테스트 데이터 생성 완료 - 회사: {}",
                    company.getCompanyName());
        }
    }
}