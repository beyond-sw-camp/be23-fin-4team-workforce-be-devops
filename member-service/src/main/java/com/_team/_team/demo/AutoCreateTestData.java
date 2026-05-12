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
     * true 일 때만 createDemoCompanies() 호출 -> 회사 4~6 (서울디지털테크/강남솔루션/판교크리에이션) 생성
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

        // 데모 회사 (1, 2, 3): toggle ON + admin1 미존재 시에만 시드 (기존 데이터 보존)
        if (seedDemoEnabled
                && memberRepository.findByEmail("admin1@workforce.com").isEmpty()) {
            log.info("[DEMO] seed.demo.enabled=true + admin1 미존재 - 데모 회사 시드 시작");
            createDemoCompanies();
        } else {
            log.info("[DEMO] 데모 회사 시드 skip (toggle={} / admin1 존재 가능)", seedDemoEnabled);
        }
    }

    /**
     * 회사 3개 (서울디지털테크/강남솔루션/판교크리에이션), 각 18명 (인사6/개발6/기획6)
     */
    private void createDemoCompanies() {
        // 가상 IT 회사명
        String[] companyNames = {
                "(주)서울디지털테크",
                "(주)강남솔루션",
                "(주)판교크리에이션"
        };
        String[] companyDomains = {
                "digitalTeck", "solution", "creation"
        };
        String[] ceoNames = { "박정훈", "이서연", "한도윤" };
        String[] adminNames = { "정직", "청렴", "결백" };
        for (int idx = 0; idx < companyNames.length; idx++) {
            int adminSeq = 1 + idx; // admin 1 / admin 2 / admin 3
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
            UUID planTeamId = organizationService.createOrganization(adminId,
                    OrganizationReqDto.builder().name("기획팀").parentId(topOrg.getOrganizationId()).build());

            // 직급/직책은 OrganizationService default 시드에서 이미 생성
            // 직급: 부장, 차장, 과장, 대리, 주임, 사원
            List<JobGrade> grades = jobGradeRepository
                    .findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(company.getCompanyId(), "NO");
            JobGrade bujang = grades.stream().filter(g -> "부장".equals(g.getName())).findFirst().orElseThrow();
            JobGrade chajang = grades.stream().filter(g -> "차장".equals(g.getName())).findFirst().orElseThrow();
            JobGrade gwajang = grades.stream().filter(g -> "과장".equals(g.getName())).findFirst().orElseThrow();
            JobGrade daeri = grades.stream().filter(g -> "대리".equals(g.getName())).findFirst().orElseThrow();
            JobGrade juim = grades.stream().filter(g -> "주임".equals(g.getName())).findFirst().orElseThrow();
            JobGrade sawon = grades.stream().filter(g -> "사원".equals(g.getName())).findFirst().orElseThrow();

            // 직책: 팀장, 파트장, 팀원
            List<JobTitle> titles = jobTitleRepository
                    .findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(company.getCompanyId(), "NO");
            JobTitle teamLeaderTitle = titles.stream().filter(t -> "팀장".equals(t.getName())).findFirst().orElseThrow();
            JobTitle partLeaderTitle = titles.stream().filter(t -> "파트장".equals(t.getName())).findFirst().orElseThrow();
            JobTitle memberTitle = titles.stream().filter(t -> "팀원".equals(t.getName())).findFirst().orElseThrow();

            List<Role> roles = roleRepository
                    .findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(company.getCompanyId(), "NO");
            // 인사 팀장 역할 - MemberService.createDefaultRoles 의 "인사 팀장"과 일치
            Role hrManagerRole = roles.stream().filter(r -> r.getName().equals("인사 팀장")).findFirst().orElseThrow();
            Role hrMemberRole = roles.stream().filter(r -> r.getName().equals("인사 팀원")).findFirst().orElseThrow();
            Role teamLeaderRole = roles.stream().filter(r -> r.getName().equals("팀장")).findFirst().orElseThrow();
            Role employeeRole = roles.stream().filter(r -> r.getName().equals("직원")).findFirst().orElseThrow();

            // 직원 18명 - 인사6 / 개발6 / 기획6
            String[] demoNames = {
                    // 인사팀 (i=0~5)
                    "정수영", "한지호", "오미경", "강대훈", "윤재성", "박서연",
                    // 개발팀 (i=6~11)
                    "이준혁", "최유진", "임도현", "조성훈", "황민지", "권시우",
                    // 기획팀 (i=12~17)
                    "송하늘", "안재현", "노유리", "유태민", "백서영", "문지원"
            };
            String[] demoInitials = {
                    "JSY", "HJH", "OMK", "KDH", "YJS", "PSY",
                    "LJH", "CYJ", "IDH", "JSH", "HMJ", "KSW",
                    "SHN", "AJH", "NYR", "YTM", "BSY", "MJW"
            };
            // 입사일(개월 전) - 팀장은 장기근속, 신입까지 다양
            int[] monthsAgo = {
                    // 인사팀: 팀장(부장), 인사관리자(과장), 인사팀원x4
                    144, 96, 60, 36, 18, 6,
                    // 개발팀: 팀장(부장), 차장, 과장, 대리, 주임, 사원
                    132, 84, 54, 30, 14, 4,
                    // 기획팀: 팀장(부장), 과장, 대리, 대리, 주임, 사원
                    120, 72, 42, 24, 10, 3
            };
            // i 별 직급/직책/role 분배
            JobGrade[] gradeByIdx = {
                    // 인사팀
                    bujang, gwajang, daeri, juim, sawon, sawon,
                    // 개발팀
                    bujang, chajang, gwajang, daeri, juim, sawon,
                    // 기획팀
                    bujang, gwajang, daeri, daeri, juim, sawon
            };
            JobTitle[] titleByIdx = {
                    // 인사팀
                    teamLeaderTitle, partLeaderTitle, memberTitle, memberTitle, memberTitle, memberTitle,
                    // 개발팀
                    teamLeaderTitle, partLeaderTitle, memberTitle, memberTitle, memberTitle, memberTitle,
                    // 기획팀
                    teamLeaderTitle, partLeaderTitle, memberTitle, memberTitle, memberTitle, memberTitle
            };
            Role[] roleByIdx = {
                    // 인사팀: 팀장, 인사관리자, 인사팀원x4
                    teamLeaderRole, hrManagerRole, hrMemberRole, hrMemberRole, hrMemberRole, hrMemberRole,
                    // 개발팀: 팀장, 일반x5
                    teamLeaderRole, employeeRole, employeeRole, employeeRole, employeeRole, employeeRole,
                    // 기획팀: 팀장, 일반x5
                    teamLeaderRole, employeeRole, employeeRole, employeeRole, employeeRole, employeeRole
            };
            UUID[] orgByIdx = {
                    hrTeamId, hrTeamId, hrTeamId, hrTeamId, hrTeamId, hrTeamId,
                    devTeamId, devTeamId, devTeamId, devTeamId, devTeamId, devTeamId,
                    planTeamId, planTeamId, planTeamId, planTeamId, planTeamId, planTeamId
            };
            LocalDate today = LocalDate.now();

            for (int i = 0; i < demoNames.length; i++) {
                String email = "emp_demo" + adminSeq + "_" + (i + 1) + "@gmail.com";

                memberService.createMember(adminId, admin.getDefaultPositionId(),
                        MemberCreateReqDto.builder()
                                .name(demoNames[i])
                                .englishInitial(demoInitials[i])
                                .personalEmail(email)
                                .joinDate(today.minusMonths(monthsAgo[i]))
                                .employmentType(EmploymentType.FULL_TIME)
                                .organizationId(orgByIdx[i])
                                .jobGradeId(gradeByIdx[i].getJobGradeId())
                                .jobTitleId(titleByIdx[i].getJobTitleId())
                                .roleId(roleByIdx[i].getRoleId())
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
        for (int c = 4; c <= 6; c++) {

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

            Role hrMemberRole = roles.stream()
                    .filter(r -> r.getName().equals("인사 팀원"))
                    .findFirst()
                    .orElseThrow();

            Role employeeRole = roles.stream()
                    .filter(r -> r.getName().equals("직원"))
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
                // i=0 인사팀장, i=1~4 인사팀원, i=5 개발팀장, i=6~9 일반 직원
                UUID roleId = (i == 0 || i == 5)
                        ? teamLeaderRole.getRoleId()
                        : (i < 5)
                        ? hrMemberRole.getRoleId()
                        : employeeRole.getRoleId();

                memberService.createMember(
                        adminId,
                        admin.getDefaultPositionId(),
                        MemberCreateReqDto.builder()
                                .name(names.get(i))
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