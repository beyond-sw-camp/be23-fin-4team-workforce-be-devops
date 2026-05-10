package com._team._team.member.service;

import com._team._team.calendar.repository.CalendarEventRepository;
import com._team._team.company.domain.Company;
import com._team._team.company.dto.reqdto.CompanyOnboardingReqDto;
import com._team._team.company.service.MailService;
import com._team._team.dto.BusinessException;
import com._team._team.saas.config.SaasOperatorProperties;
import com._team._team.dto.NotificationMessage;
import com._team._team.event.MemberChangedEvent;
import com._team._team.event.MemberDeletedEvent;
import com._team._team.event.MemberSavedEvent;
import com._team._team.member.domain.enums.MemberStatus;
import com._team._team.member.dto.resdto.MemberResDto;
import com._team._team.event.OrganizationSavedEvent;
import com._team._team.member.auth.JwtTokenProvider;
import com._team._team.annotation.Action;
import com._team._team.member.constant.PermissionRange;
import com._team._team.annotation.Resource;
import com._team._team.member.domain.*;
import com._team._team.member.domain.enums.*;
import com._team._team.member.dto.reqdto.*;
import com._team._team.member.dto.resdto.*;
import com._team._team.member.repository.*;
import com._team._team.notification.NotificationType;
import com._team._team.organization.domain.JobGrade;
import com._team._team.organization.domain.JobTitle;
import com._team._team.organization.domain.Organization;
import com._team._team.organization.repository.JobGradeRepository;
import com._team._team.organization.repository.JobTitleRepository;
import com._team._team.organization.repository.OrganizationRepository;
import com._team._team.s3.S3Uploader;
import com._team._team.util.PermissionUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberPositionRepository memberPositionRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final OrganizationRepository organizationRepository;
    private final JobGradeRepository jobGradeRepository;
    private final JobTitleRepository jobTitleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final MailService mailService;
    private final RedisTemplate<String, String> permissoinRedisTemplate;
    private final PermissionUtils permissionUtils;
    private final RedisTemplate<String, String> emailRedisTemplate;
    private final EmploymentJobHistoryRepository employmentJobHistoryRepository;
    private final S3Uploader s3Uploader;
    private final ApplicationEventPublisher eventPublisher;
    private final SearchOutboxEventRepository searchOutboxEventRepository;
    private final ObjectMapper objectMapper;
    private final CalendarEventRepository calendarEventRepository;
    private final SaasOperatorProperties saasOperatorProperties;

    @Autowired
    public MemberService(MemberRepository memberRepository, MemberPositionRepository memberPositionRepository, RoleRepository roleRepository, RolePermissionRepository rolePermissionRepository, PermissionRepository permissionRepository, OrganizationRepository organizationRepository, JobGradeRepository jobGradeRepository, JobTitleRepository jobTitleRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider, MailService mailService, @Qualifier("permissionInventory") RedisTemplate<String, String> permissoinRedisTemplate, PermissionUtils permissionUtils, @Qualifier("emailInventory") RedisTemplate<String, String> emailRedisTemplate, EmploymentJobHistoryRepository employmentJobHistoryRepository, S3Uploader s3Uploader, ApplicationEventPublisher eventPublisher, SearchOutboxEventRepository searchOutboxEventRepository, ObjectMapper objectMapper, CalendarEventRepository calendarEventRepository, SaasOperatorProperties saasOperatorProperties) {
        this.memberRepository = memberRepository;
        this.memberPositionRepository = memberPositionRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
        this.organizationRepository = organizationRepository;
        this.jobGradeRepository = jobGradeRepository;
        this.jobTitleRepository = jobTitleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.mailService = mailService;
        this.permissoinRedisTemplate = permissoinRedisTemplate;
        this.permissionUtils = permissionUtils;
        this.emailRedisTemplate = emailRedisTemplate;
        this.employmentJobHistoryRepository = employmentJobHistoryRepository;
        this.s3Uploader = s3Uploader;
        this.eventPublisher = eventPublisher;
        this.searchOutboxEventRepository = searchOutboxEventRepository;
        this.objectMapper = objectMapper;
        this.calendarEventRepository = calendarEventRepository;
        this.saasOperatorProperties = saasOperatorProperties;
    }

    // 온보딩 시 기본 역할 4개 + 권한 생성
    public void createDefaultRoles(Company company) {

        // 1. 기본 역할 4개 생성
        Role systemAdmin = createRole(company, "시스템 관리자", 1);
        Role hrManager = createRole(company, "인사 관리자", 2);
        Role teamLeader = createRole(company, "팀장", 3);
        Role employee = createRole(company, "일반 직원", 4);

        // 2. 기본 권한 세팅
        List<RolePermission> rolePermissions = new ArrayList<>();

        // 인사 관리자 권한
        rolePermissions.addAll(buildRolePermissions(hrManager, Resource.MEMBER,
                List.of(Action.CREATE, Action.READ, Action.UPDATE, Action.DELETE), PermissionRange.COMPANY));
        rolePermissions.addAll(buildRolePermissions(hrManager, Resource.ORGANIZATION,
                List.of(Action.CREATE, Action.READ, Action.UPDATE, Action.DELETE), PermissionRange.COMPANY));
        rolePermissions.addAll(buildRolePermissions(hrManager, Resource.SALARY,
                List.of(Action.READ), PermissionRange.COMPANY));
        rolePermissions.addAll(buildRolePermissions(hrManager, Resource.ATTENDANCE,
                List.of(Action.READ, Action.UPDATE), PermissionRange.COMPANY));
        rolePermissions.addAll(buildRolePermissions(hrManager, Resource.APPROVAL,
                List.of(Action.READ), PermissionRange.COMPANY));
        rolePermissions.addAll(buildRolePermissions(hrManager, Resource.ROLE,
                List.of(Action.CREATE, Action.READ, Action.UPDATE, Action.DELETE), PermissionRange.COMPANY));
        rolePermissions.addAll(buildRolePermissions(hrManager, Resource.GOAL,
                List.of(Action.READ), PermissionRange.COMPANY));
        rolePermissions.addAll(buildRolePermissions(hrManager, Resource.EVALUATION,
                List.of(Action.READ), PermissionRange.COMPANY));
        rolePermissions.addAll(buildRolePermissions(hrManager, Resource.ESG,
                List.of(Action.CREATE, Action.READ, Action.UPDATE, Action.DELETE), PermissionRange.COMPANY));
        rolePermissions.addAll(buildRolePermissions(hrManager, Resource.MEETING,
                List.of(Action.CREATE, Action.READ, Action.UPDATE, Action.DELETE), PermissionRange.COMPANY));
        rolePermissions.addAll(buildRolePermissions(hrManager, Resource.CALENDAR,
                List.of(Action.CREATE, Action.READ, Action.UPDATE, Action.DELETE), PermissionRange.COMPANY));


        // 팀장 권한
        rolePermissions.addAll(buildRolePermissions(teamLeader, Resource.MEMBER,
                List.of(Action.READ), PermissionRange.TEAM));
        rolePermissions.addAll(buildRolePermissions(teamLeader, Resource.ATTENDANCE,
                List.of(Action.READ, Action.UPDATE), PermissionRange.TEAM));
        rolePermissions.addAll(buildRolePermissions(teamLeader, Resource.SALARY,
                List.of(Action.READ), PermissionRange.TEAM));
        rolePermissions.addAll(buildRolePermissions(teamLeader, Resource.APPROVAL,
                List.of(Action.READ, Action.UPDATE), PermissionRange.TEAM));
        rolePermissions.addAll(buildRolePermissions(teamLeader, Resource.GOAL,
                List.of(Action.CREATE, Action.READ, Action.UPDATE), PermissionRange.TEAM));
        rolePermissions.addAll(buildRolePermissions(teamLeader, Resource.EVALUATION,
                List.of(Action.CREATE, Action.READ), PermissionRange.TEAM));
        rolePermissions.addAll(buildRolePermissions(teamLeader, Resource.ESG,
                List.of(Action.READ), PermissionRange.TEAM));
        rolePermissions.addAll(buildRolePermissions(teamLeader, Resource.MEETING,
                List.of(Action.CREATE, Action.READ, Action.UPDATE), PermissionRange.TEAM));
        rolePermissions.addAll(buildRolePermissions(teamLeader, Resource.CALENDAR,
                List.of(Action.CREATE, Action.READ, Action.UPDATE, Action.DELETE), PermissionRange.TEAM));


        // 일반 직원 권한
        rolePermissions.addAll(buildRolePermissions(employee, Resource.MEMBER,
                List.of(Action.READ), PermissionRange.SELF));
        rolePermissions.addAll(buildRolePermissions(employee, Resource.ATTENDANCE,
                List.of(Action.READ, Action.CREATE), PermissionRange.SELF));
        rolePermissions.addAll(buildRolePermissions(employee, Resource.SALARY,
                List.of(Action.READ), PermissionRange.SELF));
        rolePermissions.addAll(buildRolePermissions(employee, Resource.APPROVAL,
                List.of(Action.READ, Action.CREATE), PermissionRange.SELF));
        rolePermissions.addAll(buildRolePermissions(employee, Resource.GOAL,
                List.of(Action.CREATE, Action.READ), PermissionRange.SELF));
        rolePermissions.addAll(buildRolePermissions(employee, Resource.EVALUATION,
                List.of(Action.READ), PermissionRange.SELF));
        rolePermissions.addAll(buildRolePermissions(employee, Resource.ESG,
                List.of(Action.READ), PermissionRange.SELF));
        rolePermissions.addAll(buildRolePermissions(employee, Resource.MEETING,
                List.of(Action.READ), PermissionRange.SELF));
        rolePermissions.addAll(buildRolePermissions(employee, Resource.CALENDAR,
                List.of(Action.READ), PermissionRange.SELF));

        rolePermissionRepository.saveAll(rolePermissions);
    }

    public void createAdminMember(
            CompanyOnboardingReqDto reqDto,
            Company company) {

        // 이메일 중복 체크
        if (memberRepository.existsByEmail(reqDto.getAdminEmail())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "이미 사용중인 이메일입니다."
            );
        }

        // 관리자 계정 생성
        Member admin = Member.builder()
                .company(company)
                .email(reqDto.getAdminEmail())
                .password(passwordEncoder.encode(reqDto.getAdminPassword())) // 본인 비밀번호
                .name(reqDto.getAdminName())
                .memberStatus(MemberStatus.ACTIVE)
                .accountStatus(AccountStatus.ACTIVE)
                .employmentType(EmploymentType.FULL_TIME)
                .joinDate(LocalDate.now())
                .isEmailVerifiedYn("YES")  // 이미 인증 완료
                .isFirstLoginYn("NO")      // 비밀번호 직접 설정
                .isOnboardingYn("YES")
                .build();

        Member savedAdmin = memberRepository.save(admin);

        // 기본 조직/직급/직책/역할 조회
        Organization defaultOrg = organizationRepository
                .findByCompany_CompanyIdAndDelYn(company.getCompanyId(), "NO")
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "기본 조직을 찾을 수 없습니다."
                ));

        JobGrade defaultGrade = jobGradeRepository
                .findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(company.getCompanyId(), "NO")
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "기본 직급을 찾을 수 없습니다."
                ));

        JobTitle defaultTitle = jobTitleRepository
                .findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(company.getCompanyId(), "NO")
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "기본 직책을 찾을 수 없습니다."
                ));

        Role systemAdminRole = roleRepository
                .findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(company.getCompanyId(), "NO")
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "기본 역할을 찾을 수 없습니다."
                ));

        // 관리자 MemberPosition 생성
        MemberPosition position = MemberPosition.builder()
                .member(savedAdmin)
                .organization(defaultOrg)
                .jobGrade(defaultGrade)
                .jobTitle(defaultTitle)
                .role(systemAdminRole)
                .startDate(LocalDate.now())
                .isSystemAdminYn("YES")
                .build();

        MemberPosition savedPosition = memberPositionRepository.save(position);

        // defaultPositionId 업데이트
        savedAdmin.updateDefaultPosition(savedPosition.getMemberPositionId());
    }

    // 역할 생성 헬퍼
    private Role createRole(Company company, String name, int displayOrder) {
        Role role = Role.builder()
                .company(company)
                .name(name)
                .displayOrder(displayOrder)
                .build();
        return roleRepository.save(role);
    }

    // RolePermission 생성 헬퍼
    private List<RolePermission> buildRolePermissions(
            Role role,
            Resource resource,
            List<Action> actions,
            PermissionRange range) {

        List<RolePermission> result = new ArrayList<>();

        for (Action action : actions) {
            Permission permission = permissionRepository
                    .findByResourceAndAction(resource, action)
                    .orElseGet(() -> permissionRepository.save(
                            Permission.builder()
                                    .resource(resource)
                                    .action(action)
                                    .build()
                    ));

            result.add(RolePermission.builder()
                    .role(role)
                    .permission(permission)
                    .permissionRange(range)
                    .build());
        }
        return result;
    }

    //이메일 검증
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return memberRepository.existsByEmail(email);
    }

    // 권한 캐싱 메서드 추가
    private void cachePermissions(MemberPosition memberPosition) {

        if ("YES".equals(memberPosition.getIsSystemAdminYn())) return;

        List<String> permissions = memberPosition.getRole()
                .getRolePermissionList()
                .stream()
                .map(rp -> rp.getPermission().getResource().name()
                        + ":" + rp.getPermission().getAction().name()
                        + ":" + rp.getPermissionRange().name())
                .collect(Collectors.toList());

        if (permissions.isEmpty()) {
            log.warn("권한 목록이 비어있음: {}", memberPosition.getMemberPositionId());
            return;
        }

        // Redis에 저장 (30분)
        // key: "PERMISSION:{memberPositionId}"
        // value: "MEMBER:READ:COMPANY,MEMBER:CREATE:COMPANY,..."
        permissoinRedisTemplate.opsForValue().set(
                "PERMISSION:" + memberPosition.getMemberPositionId(),
                String.join(",", permissions),
                30,
                TimeUnit.MINUTES
        );
    }

    /**
     * 본인 권한 목록 조회.
     * - 로그인 응답의 {@code permissions} 와 동일한 {@code RESOURCE:ACTION:RANGE} 문자열 리스트.
     * - JWT(AT)에는 권한이 들어있지 않아, FE 새로고침 / AT 연장 후 권한 재수화 용도로 사용한다.
     * - {@code @CheckPermission} 을 걸지 않는다 (자기 자신 권한 조회는 인증된 사용자라면 항상 허용).
     * - Redis 우선, 미스 시 DB 에서 재계산하면서 캐시 재생성.
     * - 시스템 관리자는 모든 권한 통과 처리되므로 빈 리스트 반환 (FE는 isSystemAdmin 플래그로 따로 분기).
     */
    @Transactional(readOnly = true)
    public List<String> getMyPermissions(UUID memberPositionId) {
        if (memberPositionId == null) {
            return Collections.emptyList();
        }

        String cached = permissoinRedisTemplate.opsForValue().get("PERMISSION:" + memberPositionId);
        if (cached != null && !cached.isBlank()) {
            return Arrays.stream(cached.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        MemberPosition memberPosition = memberPositionRepository
                .findByIdWithRoleAndPermissions(memberPositionId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "포지션 정보를 찾을 수 없습니다."));

        // 시스템 관리자는 캐시도 만들지 않고 빈 리스트 반환 (FE는 isSystemAdmin 플래그로 처리)
        if ("YES".equals(memberPosition.getIsSystemAdminYn())) {
            return Collections.emptyList();
        }

        cachePermissions(memberPosition);

        return memberPosition.getRole()
                .getRolePermissionList()
                .stream()
                .map(rp -> rp.getPermission().getResource().name()
                        + ":" + rp.getPermission().getAction().name()
                        + ":" + rp.getPermissionRange().name())
                .collect(Collectors.toList());
    }

    // 로그인
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResDto login(LoginReqDto reqDto, HttpServletResponse response) {

        // 1. 이메일 확인
        Member member = memberRepository.findByEmail(reqDto.getEmail())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다."
                ));

        // 2. 삭제된 계정 확인 ← 추가
        if ("YES".equals(member.getDelYn())) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED, "탈퇴한 계정입니다."
            );
        }

        // 3. 계정 잠금 확인
        if (member.getAccountStatus() == AccountStatus.BLOCKED) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "계정이 잠금 상태입니다. 관리자에게 문의하세요."
            );
        }

        // 4. 비밀번호 확인
        if (!passwordEncoder.matches(reqDto.getPassword(), member.getPassword())) {
            member.increaseLoginFailCount();
            memberRepository.save(member);

            // 5회 오류 시 잠금 메시지 반환
            if (member.getAccountStatus() == AccountStatus.BLOCKED) {
                throw new BusinessException(
                        HttpStatus.FORBIDDEN, "비밀번호 5회 오류로 계정이 잠금됐습니다. 관리자에게 문의하세요."
                );
            }

            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "이메일 또는 비밀번호가 일치하지 않습니다. ("
                            + member.getLoginFailCount() + "/5)"
            );
        }

        // 5. 로그인 실패 횟수 초기화
        member.resetLoginFailCount();
        memberRepository.save(member);

        // 6. SaaS 운영자 분기 - 시스템 회사 소속이면 운영자
        boolean isSaasOperator = saasOperatorProperties.getSystemCompanyId() != null
                && member.getCompany() != null
                && saasOperatorProperties.getSystemCompanyId()
                        .equals(member.getCompany().getCompanyId().toString());
        if (isSaasOperator) {
            String operatorAt = jwtTokenProvider.createAtToken(member, null);
            String operatorRt = jwtTokenProvider.createRtToken(member);
            jwtTokenProvider.setRtCookie(response, operatorRt);
            return LoginResDto.builder()
                    .accessToken(operatorAt)
                    .name(member.getName())
                    .memberId(member.getMemberId())
                    .memberPositionId(null)
                    .isFirstLoginYn(member.getIsFirstLoginYn())
                    .isEmailVerifiedYn(member.getIsEmailVerifiedYn())
                    .isOnboardingYn(member.getIsOnboardingYn())
                    .permissions(List.of())
                    .build();
        }

        // 7. defaultPosition 조회
        MemberPosition memberPosition = memberPositionRepository
                .findByIdWithRoleAndPermissions(member.getDefaultPositionId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "포지션 정보를 찾을 수 없습니다."
                ));

        // 8. AT 발급
        String accessToken = jwtTokenProvider.createAtToken(member, memberPosition);

        // 9. RT 발급 + 쿠키 저장
        String refreshToken = jwtTokenProvider.createRtToken(member);
        jwtTokenProvider.setRtCookie(response, refreshToken);

        cachePermissions(memberPosition);

        // 권한 목록 조회
        List<String> permissions = rolePermissionRepository
                .findByRole(memberPosition.getRole())
                .stream()
                .map(rp -> rp.getPermission().getResource().name()
                        + ":" + rp.getPermission().getAction().name()
                        + ":" + rp.getPermissionRange().name())
                .collect(Collectors.toList());

        return LoginResDto.builder()
                .accessToken(accessToken)
                .name(member.getName())
                .memberId(member.getMemberId())
                .memberPositionId(memberPosition.getMemberPositionId())
                .isFirstLoginYn(member.getIsFirstLoginYn())
                .isEmailVerifiedYn(member.getIsEmailVerifiedYn())
                .isOnboardingYn(member.getIsOnboardingYn())
                .permissions(permissions)
                .build();
    }

    public void changePassword(UUID memberId, ChangePasswordReqDto reqDto) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        // 1. 현재 비밀번호 확인
        if (!passwordEncoder.matches(reqDto.getCurrentPassword(), member.getPassword())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."
            );
        }

        // 2. 새 비밀번호 확인
        if (!reqDto.getNewPassword().equals(reqDto.getNewPasswordCheck())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "새 비밀번호가 일치하지 않습니다."
            );
        }

        // 3. 현재 비밀번호와 새 비밀번호 같은지 확인
        if (passwordEncoder.matches(reqDto.getNewPassword(), member.getPassword())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "현재 비밀번호와 다른 비밀번호를 입력해주세요."
            );
        }

        // 4. 비밀번호 변경
        member.updatePassword(passwordEncoder.encode(reqDto.getNewPassword()));

        // 5. 최초 로그인 완료 처리
        member.completeFirstLogin();
    }

    // 로그아웃
    public void logout(UUID memberId, HttpServletResponse response) {
        // Redis RT 삭제
        jwtTokenProvider.deleteRtToken(memberId.toString());
        // 쿠키 삭제
        jwtTokenProvider.deleteRtCookie(response);
    }

    // AT 재발급
    public LoginResDto generateAt(UUID memberId,
                                  UUID memberPositionId,
                                  HttpServletRequest request) {

        jwtTokenProvider.validateAndGetRtClaims(request, memberId.toString());

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."));

        if ("YES".equals(member.getDelYn())) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED, "탈퇴한 계정입니다.");
        }

        if (member.getAccountStatus() == AccountStatus.BLOCKED) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "계정이 잠금 상태입니다.");
        }

        MemberPosition memberPosition = memberPositionRepository
                .findByIdWithRoleAndPermissions(memberPositionId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 포지션입니다."));

        String accessToken = jwtTokenProvider.createAtToken(member, memberPosition);

        // 권한 캐시 재캐싱 추가
        cachePermissions(memberPosition);

        return LoginResDto.builder()
                .accessToken(accessToken)
                .memberId(memberId)
                .memberPositionId(memberPositionId)
                .build();
    }

    // 직원 계정 생성
    public void createMember(UUID memberId, UUID memberPositionId, MemberCreateReqDto reqDto) {

        // 1. 요청자 조회
        Member requester = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));
        Company company = requester.getCompany();

        String sabun = generateSabun(company);

        // 2. 회사 이메일 자동 생성
        String companyDomain = company.getCompanyDomain();

        String companyEmail = sabun.toLowerCase()
                + "." + reqDto.getEnglishInitial().toUpperCase()
                + "@" + companyDomain + ".com";

        // 3. 중복 체크
        if (memberRepository.existsByEmail(companyEmail)) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 사용중인 회사 이메일입니다.");
        }
        if (memberRepository.existsByPersonalEmail(reqDto.getPersonalEmail())) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 사용중인 개인 이메일입니다.");
        }


        // 4. 임시 비밀번호 생성
        String tempPassword = generateTempPassword();

        // 5. 직원 계정 생성
        Member member = reqDto.toEntity(companyEmail, passwordEncoder.encode(tempPassword), company, sabun);
        Member savedMember = memberRepository.save(member);

        // 6. 조직 조회 + 같은 회사 검증
        Organization organization = organizationRepository
                .findById(reqDto.getOrganizationId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 조직입니다."));

        if (!organization.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "다른 회사의 조직에 직원을 추가할 수 없습니다.");
        }

        // 7. 직급 조회 + 같은 회사 검증
        JobGrade jobGrade = jobGradeRepository
                .findById(reqDto.getJobGradeId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 직급입니다."));

        if (!jobGrade.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "다른 회사의 직급을 지정할 수 없습니다.");
        }

        // 8. 직책 조회 + 같은 회사 검증
        JobTitle jobTitle = jobTitleRepository
                .findById(reqDto.getJobTitleId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 직책입니다."));

        if (!jobTitle.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "다른 회사의 직책을 지정할 수 없습니다.");
        }

        // 9. 역할 조회 + 같은 회사 검증
        Role role = roleRepository
                .findById(reqDto.getRoleId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 역할입니다."));

        if (!role.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "다른 회사의 역할을 지정할 수 없습니다.");
        }

        // 10. MemberPosition 생성
        MemberPosition position = MemberPosition.builder()
                .member(savedMember)
                .organization(organization)
                .jobGrade(jobGrade)
                .jobTitle(jobTitle)
                .role(role)
                .startDate(reqDto.getJoinDate())
                .isSystemAdminYn("NO")
                .build();

        MemberPosition savedPosition = memberPositionRepository.save(position);

        // 11. defaultPositionId 업데이트
        savedMember.updateDefaultPosition(savedPosition.getMemberPositionId());
        // 12. 최초 등록 이력 저장
        EmploymentJobHistory history = EmploymentJobHistory.builder()
                .member(savedMember)
                .jobGrade(jobGrade)
                .organization(organization)
                .defaultPosition(savedPosition)
                .employmentType(reqDto.getEmploymentType())
                .changedAt(LocalDateTime.now())
                .changedId(memberId)          // 생성한 인사팀 ID
                .effectiveFrom(reqDto.getJoinDate())
                .effectiveTo(null)            // 현재 적용 중
                .changeType(ChangeType.INITIAL)
                .build();

        employmentJobHistoryRepository.save(history);

        // 임시 비밀번호 로그로 보기
        log.info("Email: {}, TempPassword: {}", companyEmail, tempPassword);

        // 13. 임시 비밀번호 개인 이메일로 발송
        // 데모 시드 부팅 가속 위해 임시 주석 - 운영 배포 시 풀기
        // mailService.sendTempPassword(reqDto.getPersonalEmail(), companyEmail, tempPassword);

        // 14. 아웃박스 저장
        eventPublisher.publishEvent(new MemberChangedEvent(savedMember.getMemberId()));
    }

    // 직원 목록 조회
    @Transactional(readOnly = true)
    public List<MemberResDto> getMemberList(UUID memberId, UUID memberPositionId) {

        Member requester = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        Organization organization = memberPositionRepository
                .findById(memberPositionId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 포지션입니다."
                ))
                .getOrganization();

        return permissionUtils.getDataByRange(
                memberPositionId.toString(),
                Resource.MEMBER,
                Action.READ,

                // COMPANY → 전사 조회 (fetch join으로 N+1 방지)
                () -> memberRepository
                        .findByCompanyAndDelYnWithPosition(requester.getCompany(), "NO")
                        .stream()
                        .map(m -> memberPositionRepository
                                .findByIdWithDetails(m.getDefaultPositionId())
                                .map(pos -> MemberResDto.fromEntity(m, pos))
                                .orElse(MemberResDto.fromEntity(m)))
                        .toList(),


                // TEAM/DEPARTMENT → 같은 조직 조회
                () -> memberPositionRepository
                        .findByOrganizationAndDelYn(organization, "NO")
                        .stream()
                        .map(mp -> MemberResDto.fromEntity(mp.getMember(), mp))
                        .toList(),

                // SELF → 본인만
                () -> memberPositionRepository
                        .findByIdWithDetails(requester.getDefaultPositionId())
                        .map(pos -> List.of(MemberResDto.fromEntity(requester, pos)))
                        .orElse(List.of(MemberResDto.fromEntity(requester)))
        );
    }

    // 직원 상세 조회
    @Transactional(readOnly = true)
    public MemberDetailResDto getMemberDetail(UUID memberId,
                                              UUID memberPositionId,
                                              UUID targetMemberId) {

        Member requester = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."));

        Member target = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));

        if (!target.getCompany().getCompanyId()
                .equals(requester.getCompany().getCompanyId())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "다른 회사의 직원을 조회할 수 없습니다.");
        }

        MemberPosition position = memberPositionRepository
                .findByIdWithDetails(target.getDefaultPositionId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "포지션 정보를 찾을 수 없습니다."));

        // 본인 조회면 전체 정보, 타인이면 공개 여부 체크
        if (memberId.equals(targetMemberId)) {
            return MemberDetailResDto.fromEntitySelf(target, position);
        }
        return MemberDetailResDto.fromEntity(target, position);
    }

    // 사원 검색 (QueryDSL 동적쿼리 + 페이징)
    // 같은 회사로 강제 락, 호출자 회사 ID를 condition에 덮어쓴다
    // 권한 분기는 일단 회사단위만, 세분화는 추후
    @Transactional(readOnly = true)
    public Page<MemberSearchItemResDto> searchMembers(
            UUID memberId,
            MemberSearchCondition condition,
            org.springframework.data.domain.Pageable pageable) {

        // 호출자 조회 → 회사 강제 주입 (다른 회사 못 보게)
        Member requester = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."));

        UUID companyId = requester.getCompany().getCompanyId();
        MemberSearchCondition safeCond = MemberSearchCondition.builder()
                .keyword(condition.getKeyword())
                .companyId(companyId)
                .organizationId(condition.getOrganizationId())
                .jobTitleId(condition.getJobTitleId())
                .jobGradeId(condition.getJobGradeId())
                .memberStatus(condition.getMemberStatus())
                .employmentType(condition.getEmploymentType())
                .joinDateFrom(condition.getJoinDateFrom())
                .joinDateTo(condition.getJoinDateTo())
                .includeDeleted(condition.isIncludeDeleted())
                .build();

        return memberRepository.searchMembers(safeCond, pageable);
    }

    // 임시 비밀번호 생성
    private String generateTempPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // 비밀번호 재설정 인증 코드 발송
    public void sendResetPasswordCode(String personalEmail) {

        // 1. 개인 이메일로 회원 조회
        Member member = memberRepository.findByPersonalEmail(personalEmail)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 이메일입니다."
                ));

        // 2. 계정 상태 확인
        if (member.getAccountStatus() == AccountStatus.BLOCKED) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "계정이 잠금 상태입니다. 관리자에게 문의하세요."
            );
        }

        // 3. 인증 코드 생성
        String code = generateVerificationCode();

        // 4. Redis DB1에 저장 (5분)
        emailRedisTemplate.opsForValue().set(
                "RESET_PASSWORD:" + personalEmail,
                code,
                5,
                TimeUnit.MINUTES
        );

        // 5. 메일 발송
        mailService.sendVerificationCode(personalEmail, code);
    }

    // 비밀번호 재설정 인증 코드 확인
    public void verifyResetPasswordCode(String personalEmail, String code) {

        String savedCode = emailRedisTemplate.opsForValue()
                .get("RESET_PASSWORD:" + personalEmail);

        if (savedCode == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "인증 코드가 만료됐습니다."
            );
        }

        if (!savedCode.equals(code)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "인증 코드가 일치하지 않습니다."
            );
        }

        // 인증 완료 표시 (30분)
        emailRedisTemplate.opsForValue().set(
                "RESET_PASSWORD_VERIFIED:" + personalEmail,
                "YES",
                30,
                TimeUnit.MINUTES
        );

        // 인증 코드 삭제
        emailRedisTemplate.delete("RESET_PASSWORD:" + personalEmail);
    }

    // 비밀번호 재설정
    public void resetPassword(ResetPasswordReqDto reqDto) {

        // 1. 이메일 인증 여부 확인
        String verified = emailRedisTemplate.opsForValue()
                .get("RESET_PASSWORD_VERIFIED:" + reqDto.getPersonalEmail());

        if (!"YES".equals(verified)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "이메일 인증이 필요합니다."
            );
        }

        // 2. 새 비밀번호 확인
        if (!reqDto.getNewPassword().equals(reqDto.getNewPasswordCheck())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "새 비밀번호가 일치하지 않습니다."
            );
        }

        // 3. 회원 조회
        Member member = memberRepository.findByPersonalEmail(reqDto.getPersonalEmail())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 이메일입니다."
                ));

        // 4. 현재 비밀번호와 같은지 확인
        if (passwordEncoder.matches(reqDto.getNewPassword(), member.getPassword())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "현재 비밀번호와 다른 비밀번호를 입력해주세요."
            );
        }

        // 5. 비밀번호 변경
        member.updatePassword(passwordEncoder.encode(reqDto.getNewPassword()));

        // 6. 인증 정보 삭제
        emailRedisTemplate.delete("RESET_PASSWORD_VERIFIED:" + reqDto.getPersonalEmail());
    }

    // 인증 코드 생성
    private String generateVerificationCode() {
        SecureRandom random = new SecureRandom();
        int code = random.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    // 직원 수정 (인사팀)
    public void updateMember(UUID memberId, UUID memberPositionId,
                             UUID targetMemberId, MemberUpdateReqDto reqDto) {

        // 1. 요청자 조회
        Member requester = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));
        Company company = requester.getCompany();

        // 2. 대상 직원 조회
        Member target = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."
                ));

        // 3. 같은 회사 직원인지 확인
        if (!target.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "다른 회사의 직원을 수정할 수 없습니다."
            );
        }

        // 4. 조직/직급/직책/역할 조회 + 같은 회사 검증
        Organization organization = organizationRepository
                .findById(reqDto.getOrganizationId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 조직입니다."
                ));

        JobGrade jobGrade = jobGradeRepository
                .findById(reqDto.getJobGradeId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 직급입니다."
                ));

        JobTitle jobTitle = jobTitleRepository
                .findById(reqDto.getJobTitleId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 직책입니다."
                ));

        Role role = roleRepository
                .findById(reqDto.getRoleId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 역할입니다."
                ));

        // 5. 현재 이력 조회
        EmploymentJobHistory currentHistory = employmentJobHistoryRepository
                .findCurrentHistory(target)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "이력 정보를 찾을 수 없습니다."
                ));

        // 6. 변경 유형 판별
        ChangeType changeType = determineChangeType(
                currentHistory, reqDto, Boolean.TRUE.equals(reqDto.getIsPromotion())
        );

        // 7. 기본 정보 수정
        target.updateBasicInfo(
                reqDto.getName(),
                reqDto.getSabun(),
                reqDto.getJoinDate(),
                reqDto.getEmploymentType(),
                reqDto.getMemberStatus()
        );

        // 8. MemberPosition 수정
        MemberPosition position = memberPositionRepository
                .findById(target.getDefaultPositionId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "포지션 정보를 찾을 수 없습니다."
                ));

        position.update(organization, jobGrade, jobTitle, role);

        // 9. 변경 이력 저장
        if (changeType != null) {
            // 기존 이력 종료
            currentHistory.closeHistory(LocalDate.now());

            // 새 이력 생성
            EmploymentJobHistory newHistory = EmploymentJobHistory.builder()
                    .member(target)
                    .jobGrade(jobGrade)
                    .organization(organization)
                    .defaultPosition(position)
                    .employmentType(reqDto.getEmploymentType())
                    .changedAt(LocalDateTime.now())
                    .changedId(memberId)
                    .changeReason(reqDto.getChangeReason())
                    .effectiveFrom(LocalDate.now())
                    .effectiveTo(null)
                    .changeType(changeType)
                    .build();

            employmentJobHistoryRepository.save(newHistory);
        }
        // 아웃박스 저장
        eventPublisher.publishEvent(new MemberChangedEvent(targetMemberId));

        // 알림 발행
        eventPublisher.publishEvent(NotificationMessage.builder()
                .receiverId(targetMemberId)
                .senderId(memberId)
                .notificationType(NotificationType.MEMBER_INFO_UPDATED)
                .content("인사 정보가 수정됐습니다.")
                .targetId(targetMemberId)
                .targetType("MEMBER")
                .build());
    }

    // 변경 유형 판별
    private ChangeType determineChangeType(
            EmploymentJobHistory current,
            MemberUpdateReqDto reqDto,
            boolean isPromotion) {

        // 직급 변경
        if (!current.getJobGrade().getJobGradeId().equals(reqDto.getJobGradeId())) {
            return isPromotion ? ChangeType.PROMOTION : ChangeType.GRADE_CHANGE;
        }

        // 조직 변경
        if (!current.getOrganization().getOrganizationId().equals(reqDto.getOrganizationId())) {
            return ChangeType.ORG_CHANGE;
        }

        // 고용형태 변경
        if (!current.getEmploymentType().equals(reqDto.getEmploymentType())) {
            return ChangeType.EMPLOYMENT_CHANGE;
        }

        // 직책 변경
        if (!current.getDefaultPosition().getJobTitle().getJobTitleId()
                .equals(reqDto.getJobTitleId())) {
            return ChangeType.TITLE_CHANGE;
        }

        // 변경 없음
        return null;
    }

    // 마이페이지 수정 (본인)
    public void updateMyInfo(UUID memberId, MyInfoUpdateReqDto reqDto) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        member.updateMyInfo(
                reqDto.getPhoneNumber(),
                reqDto.getPhonePublicYn(),
                reqDto.getEmergencyContact(),
                reqDto.getAddress(),
                reqDto.getDetailAddress(),
                reqDto.getAddressPublicYn(),
                reqDto.getBank(),
                reqDto.getBankAccount(),
                reqDto.getExtensionNumber(),
                reqDto.getTelNumber()
        );

        eventPublisher.publishEvent(
                new MemberChangedEvent(memberId));
    }

    // 직원 삭제
    public void deleteMember(UUID memberId, UUID targetMemberId) {

        // 1. 요청자 조회
        Member requester = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        // 2. 대상 직원 조회
        Member target = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."
                ));

        // 3. 같은 회사인지 확인
        if (!target.getCompany().getCompanyId()
                .equals(requester.getCompany().getCompanyId())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "다른 회사의 직원을 삭제할 수 없습니다."
            );
        }

        // 4. 이미 삭제된 직원인지 확인
        if ("YES".equals(target.getDelYn())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "이미 삭제된 직원입니다."
            );
        }

        // 5. 본인 삭제 방지
        if (memberId.equals(targetMemberId)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "본인 계정은 삭제할 수 없습니다."
            );
        }

        // 6. 소프트 삭제
        target.delete();

        // 7. 계정 상태 변경
        target.updateMemberStatus(MemberStatus.LEAVE);

        // 8. Redis 권한 캐시 삭제
        MemberPosition position = memberPositionRepository
                .findById(target.getDefaultPositionId())
                .orElse(null);

        if (position != null) {
            permissoinRedisTemplate.delete(
                    "PERMISSION:" + position.getMemberPositionId()
            );
        }
        saveSearchOutboxDeleteEvent(targetMemberId);
    }

    // 직원 복원
    public void restoreMember(UUID memberId, UUID targetMemberId) {

        // 1. 요청자 조회
        Member requester = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        // 2. 대상 직원 조회
        Member target = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."
                ));

        // 3. 같은 회사인지 확인
        if (!target.getCompany().getCompanyId()
                .equals(requester.getCompany().getCompanyId())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "다른 회사의 직원을 복원할 수 없습니다."
            );
        }

        // 4. 삭제된 직원인지 확인
        if ("NO".equals(target.getDelYn())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "삭제되지 않은 직원입니다."
            );
        }

        // 5. 복원
        target.restore();

        // 6. 계정 상태 변경
        target.updateMemberStatus(MemberStatus.ACTIVE);

        eventPublisher.publishEvent(
                new MemberChangedEvent(targetMemberId));

    }

    public void updateMemberRole(UUID memberId, UUID targetMemberId,
                                 UpdateMemberRoleReqDto reqDto) {

        // 1. 요청자 조회
        Member requester = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        // 2. 대상 직원 MemberPosition 조회
        Member target = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."
                ));

        // 3. 같은 회사 확인
        if (!target.getCompany().getCompanyId()
                .equals(requester.getCompany().getCompanyId())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "다른 회사의 직원입니다."
            );
        }

        // 4. 역할 조회
        Role role = roleRepository.findById(reqDto.getRoleId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 역할입니다."
                ));

        // 5. 같은 회사 역할인지 확인
        if (!role.getCompany().getCompanyId()
                .equals(requester.getCompany().getCompanyId())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "다른 회사의 역할입니다."
            );
        }

        // 6. MemberPosition 역할 변경
        MemberPosition position = memberPositionRepository
                .findById(target.getDefaultPositionId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "포지션 정보를 찾을 수 없습니다."
                ));

        position.updateRole(role);

        // 7. Redis 권한 캐시 삭제 (다음 로그인 시 재캐싱)
        permissoinRedisTemplate.delete(
                "PERMISSION:" + position.getMemberPositionId()
        );
    }

    // 직원 이력 조회
    @Transactional(readOnly = true)
    public List<EmploymentJobHistoryResDto> getMemberHistory(
            UUID memberId, UUID targetMemberId) {

        // 1. 요청자 조회
        Member requester = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        // 2. 대상 직원 조회
        Member target = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."
                ));

        // 3. 같은 회사인지 확인
        if (!target.getCompany().getCompanyId()
                .equals(requester.getCompany().getCompanyId())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "다른 회사의 직원 이력을 조회할 수 없습니다."
            );
        }

        // 4. 이력 조회 (최신순)
        return employmentJobHistoryRepository
                .findByMemberOrderByEffectiveFromDesc(target)
                .stream()
                .map(history -> {
                    // 변경자 이름 조회
                    String changerName = memberRepository
                            .findById(history.getChangedId())
                            .map(Member::getName)
                            .orElse("알 수 없음");
                    return EmploymentJobHistoryResDto.fromEntity(history, changerName);
                })
                .collect(Collectors.toList());
    }

    // 역할 생성
    public UUID createRole(UUID memberId, RoleReqDto reqDto) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));
        Company company = member.getCompany();

        int displayOrder = roleRepository
                .findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(
                        company.getCompanyId(), "NO").size();

        Role role = Role.builder()
                .company(company)
                .name(reqDto.getName())
                .description(reqDto.getDescription())
                .displayOrder(displayOrder)
                .build();

        Role savedRole = roleRepository.save(role);

        // 권한 세팅
        if (reqDto.getPermissions() != null && !reqDto.getPermissions().isEmpty()) {
            List<RolePermission> rolePermissions = reqDto.getPermissions().stream()
                    .map(p -> {
                        Permission permission = permissionRepository
                                .findByResourceAndAction(p.getResource(), p.getAction())
                                .orElseGet(() -> permissionRepository.save(
                                        Permission.builder()
                                                .resource(p.getResource())
                                                .action(p.getAction())
                                                .build()
                                ));
                        return RolePermission.builder()
                                .role(savedRole)
                                .permission(permission)
                                .permissionRange(p.getPermissionRange())
                                .build();
                    })
                    .collect(Collectors.toList());

            rolePermissionRepository.saveAll(rolePermissions);
        }

        return savedRole.getRoleId();
    }

    // 역할 목록 조회
    @Transactional(readOnly = true)
    public List<RoleResDto> getRoleList(UUID memberId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        return roleRepository
                .findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(
                        member.getCompany().getCompanyId(), "NO")
                .stream()
                .map(RoleResDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 역할 상세 조회
    @Transactional(readOnly = true)
    public RoleResDto getRoleDetail(UUID memberId, UUID roleId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 역할입니다."
                ));

        if (!role.getCompany().getCompanyId()
                .equals(member.getCompany().getCompanyId())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "다른 회사의 역할입니다."
            );
        }

        return RoleResDto.fromEntity(role);
    }

    // 역할 수정
    public void updateRole(UUID memberId, UUID roleId, RoleReqDto reqDto) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 역할입니다."
                ));

        if (!role.getCompany().getCompanyId()
                .equals(member.getCompany().getCompanyId())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "다른 회사의 역할입니다."
            );
        }

        // 역할 기본 정보 수정
        role.updateName(reqDto.getName());
        role.updateDescription(reqDto.getDescription());

        // 기존 권한 삭제 후 새로 저장
        rolePermissionRepository.deleteByRole(role);

        if (reqDto.getPermissions() != null && !reqDto.getPermissions().isEmpty()) {
            List<RolePermission> rolePermissions = reqDto.getPermissions().stream()
                    .map(p -> {
                        Permission permission = permissionRepository
                                .findByResourceAndAction(p.getResource(), p.getAction())
                                .orElseGet(() -> permissionRepository.save(
                                        Permission.builder()
                                                .resource(p.getResource())
                                                .action(p.getAction())
                                                .build()
                                ));
                        return RolePermission.builder()
                                .role(role)
                                .permission(permission)
                                .permissionRange(p.getPermissionRange())
                                .build();
                    })
                    .collect(Collectors.toList());

            rolePermissionRepository.saveAll(rolePermissions);
        }

        // Redis 권한 캐시 삭제 (해당 역할 사용 직원 전체)
        memberPositionRepository.findByRole(role)
                .forEach(mp -> permissoinRedisTemplate.delete(
                        "PERMISSION:" + mp.getMemberPositionId()
                ));
    }

    // 역할 삭제
    public void deleteRole(UUID memberId, UUID roleId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 역할입니다."
                ));

        if (!role.getCompany().getCompanyId()
                .equals(member.getCompany().getCompanyId())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "다른 회사의 역할입니다."
            );
        }

        // 사용 중인 직원 확인
        if (memberPositionRepository.existsByRoleAndDelYn(role, "NO")) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "사용 중인 직원이 있어 삭제할 수 없습니다."
            );
        }

        role.delete();
    }

    // 계정 잠금 해제
    public void unblockMember(UUID memberId, UUID targetMemberId) {

        // 1. 요청자 조회
        Member requester = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        // 2. 대상 직원 조회
        Member target = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."
                ));

        // 3. 같은 회사인지 확인
        if (!target.getCompany().getCompanyId()
                .equals(requester.getCompany().getCompanyId())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "다른 회사의 직원입니다."
            );
        }

        // 4. 잠금 상태인지 확인
        if (target.getAccountStatus() != AccountStatus.BLOCKED) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "잠금 상태가 아닌 계정입니다."
            );
        }

        // 5. 잠금 해제
        target.unblock();
    }

    // 프로필 이미지 업로드
    public void updateProfileImage(UUID memberId, MultipartFile profileImage) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        // 기존 이미지 있으면 삭제
        if (member.getProfileUrl() != null) {
            s3Uploader.delete(member.getProfileUrl());
        }

        // 새 이미지 업로드
        String profileUrl = s3Uploader.upload(profileImage, "profile");

        // profileUrl 업데이트
        member.updateProfileUrl(profileUrl);
    }

    // 프로필 이미지 삭제
    public void deleteProfileImage(UUID memberId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        if (member.getProfileUrl() == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "프로필 이미지가 없습니다."
            );
        }

        // S3 파일 삭제
        s3Uploader.delete(member.getProfileUrl());

        // profileUrl null로 업데이트
        member.updateProfileUrl(null);
    }

    // 휴직 처리
    public void dormantMember(UUID memberId, UUID targetMemberId) {

        // 1. 요청자 조회
        Member requester = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        // 2. 대상 직원 조회
        Member target = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."
                ));

        // 3. 같은 회사인지 확인
        if (!target.getCompany().getCompanyId()
                .equals(requester.getCompany().getCompanyId())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "다른 회사의 직원입니다."
            );
        }

        // 4. 이미 휴직 중인지 확인
        if (target.getMemberStatus() == MemberStatus.DORMANT) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "이미 휴직 중인 직원입니다."
            );
        }

        // 5. 퇴직한 직원인지 확인
        if ("YES".equals(target.getDelYn())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "퇴직한 직원입니다."
            );
        }

        // 6. 휴직 처리
        target.updateMemberStatus(MemberStatus.DORMANT);

        // 7. 이력 저장
        EmploymentJobHistory currentHistory = employmentJobHistoryRepository
                .findCurrentHistory(target)
                .orElse(null);

        if (currentHistory != null) {
            currentHistory.closeHistory(LocalDate.now());
        }

        MemberPosition position = memberPositionRepository
                .findById(target.getDefaultPositionId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "포지션 정보를 찾을 수 없습니다."
                ));

        EmploymentJobHistory history = EmploymentJobHistory.builder()
                .member(target)
                .jobGrade(position.getJobGrade())
                .organization(position.getOrganization())
                .defaultPosition(position)
                .employmentType(target.getEmploymentType())
                .changedAt(LocalDateTime.now())
                .changedId(memberId)
                .effectiveFrom(LocalDate.now())
                .effectiveTo(null)
                .changeType(ChangeType.DORMANT)
                .build();

        employmentJobHistoryRepository.save(history);
        eventPublisher.publishEvent(
                new MemberChangedEvent(targetMemberId));
    }

    // 복직 처리
    public void returnMember(UUID memberId, UUID targetMemberId) {

        // 1. 요청자 조회
        Member requester = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        // 2. 대상 직원 조회
        Member target = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."
                ));

        // 3. 같은 회사인지 확인
        if (!target.getCompany().getCompanyId()
                .equals(requester.getCompany().getCompanyId())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "다른 회사의 직원입니다."
            );
        }

        // 4. 휴직 중인지 확인
        if (target.getMemberStatus() != MemberStatus.DORMANT) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "휴직 중인 직원이 아닙니다."
            );
        }

        // 5. 복직 처리
        target.updateMemberStatus(MemberStatus.ACTIVE);

        // 6. 이력 저장
        EmploymentJobHistory currentHistory = employmentJobHistoryRepository
                .findCurrentHistory(target)
                .orElse(null);

        if (currentHistory != null) {
            currentHistory.closeHistory(LocalDate.now());
        }

        MemberPosition position = memberPositionRepository
                .findById(target.getDefaultPositionId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "포지션 정보를 찾을 수 없습니다."
                ));

        EmploymentJobHistory history = EmploymentJobHistory.builder()
                .member(target)
                .jobGrade(position.getJobGrade())
                .organization(position.getOrganization())
                .defaultPosition(position)
                .employmentType(target.getEmploymentType())
                .changedAt(LocalDateTime.now())
                .changedId(memberId)
                .effectiveFrom(LocalDate.now())
                .effectiveTo(null)
                .changeType(ChangeType.RETURN)
                .build();

        employmentJobHistoryRepository.save(history);

        eventPublisher.publishEvent(
                new MemberChangedEvent(targetMemberId));
    }

    // 사번 자동 생성
    private String generateSabun(Company company) {
        List<String> sabunList = memberRepository
                .findLastSabunByCompanyWithLock(company);

        if (sabunList.isEmpty()) {
            return "EMP0001";
        }

        // 마지막 사번에서 숫자 추출
        String lastSabun = sabunList.get(0);
        int lastNumber = Integer.parseInt(
                lastSabun.replace("EMP", ""));

        // 숫자 + 1 후 4자리로 포맷
        return String.format("EMP%04d", lastNumber + 1);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void handleMemberSaveEvent(MemberChangedEvent event) {
        saveSearchOutboxEvent(event.getMemberId());
    }

    public void saveSearchOutboxEvent(UUID memberId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."
                ));

        try {
            MemberPosition position = memberPositionRepository
                    .findByIdWithDetails(member.getDefaultPositionId())
                    .orElseThrow(() -> new BusinessException(
                            HttpStatus.NOT_FOUND, "포지션 정보를 찾을 수 없습니다."
                    ));

            Organization org = position.getOrganization();

            List<OrganizationSavedEvent> organizationList =
                    List.of(OrganizationSavedEvent.builder()
                            .organizationId(org.getOrganizationId())
                            .name(org.getName())
                            .parentId(org.getParent() != null
                                    ? org.getParent().getOrganizationId()
                                    : null)
                            .companyId(member.getCompany().getCompanyId())
                            .build());

            MemberSavedEvent savedEvent = MemberSavedEvent.builder()
                    .memberId(member.getMemberId())
                    .companyId(member.getCompany().getCompanyId())
                    .name(member.getName())
                    .organizationList(organizationList)
                    .titleName(List.of(position.getJobTitle().getName()))
                    .phoneNumber(member.getPhonePublicYn().equals("YES")
                            ? member.getPhoneNumber() : null)
                    .email(member.getEmail())
                    .memberStatus(member.getMemberStatus().name())
                    .position(position.getJobTitle().getName())
                    // 추가
                    .sabun(member.getSabun())
                    .joinDate(member.getJoinDate())
                    .jobGradeName(position.getJobGrade().getName())
                    .esgScore(member.getEsgScore())
                    .build();

            String payload = objectMapper.writeValueAsString(savedEvent);

            SearchOutboxEvent outboxEvent = SearchOutboxEvent.builder()
                    .topic("member-saved")
                    .aggregateId(member.getMemberId())
                    .payload(payload)
                    .processed("NO")
                    .createdAt(LocalDateTime.now())
                    .build();

            searchOutboxEventRepository.save(outboxEvent);

        } catch (Exception e) {
            log.error("SearchOutboxEvent 저장 실패: {}", e.getMessage());
        }
    }

    private void saveSearchOutboxDeleteEvent(UUID memberId) {
        try {
            MemberDeletedEvent event = MemberDeletedEvent.builder()
                    .memberId(memberId)
                    .build();

            String payload = objectMapper.writeValueAsString(event);

            SearchOutboxEvent outboxEvent = SearchOutboxEvent.builder()
                    .topic("member-deleted")
                    .aggregateId(memberId)
                    .payload(payload)
                    .processed("NO")
                    .createdAt(LocalDateTime.now())
                    .build();

            searchOutboxEventRepository.save(outboxEvent);

        } catch (Exception e) {
            log.error("SearchOutboxDeleteEvent 저장 실패: {}", e.getMessage());
        }
    }

    /**
     * 회사별 시스템 관리자 조회
     * 알림 발송 대상 (예: 주 52시간 위반 알림)
     */
    @Transactional(readOnly = true)
    public List<MemberResDto> findAdminsByCompanyId(UUID companyId) {
        List<Member> members = memberRepository
                .findAllActiveByCompanyId(companyId, MemberStatus.ACTIVE);
        if (members.isEmpty()) return java.util.Collections.emptyList();
        java.util.Set<UUID> memberIds = members.stream()
                .map(Member::getMemberId)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<UUID, MemberPosition> positionByMember = memberPositionRepository
                .findActivePositionsByMemberIds(memberIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        mp -> mp.getMember().getMemberId(),
                        mp -> mp,
                        (a, b) -> a));
        return members.stream()
                .filter(m -> {
                    MemberPosition mp = positionByMember.get(m.getMemberId());
                    return mp != null && "YES".equals(mp.getIsSystemAdminYn());
                })
                .map(m -> {
                    MemberPosition mp = positionByMember.get(m.getMemberId());
                    return MemberResDto.fromEntity(m, mp);
                })
                .toList();
    }

    /**
     * 회사별 재직 사원 조회
     */
    @Transactional(readOnly = true)
    public List<MemberResDto> findAllByCompanyIdForBatch(UUID companyId) {
        // 1 활성 직원 조회
        List<Member> members = memberRepository
                .findAllActiveByCompanyId(companyId, MemberStatus.ACTIVE);
        if (members.isEmpty()) return java.util.Collections.emptyList();

        // 2 활성 포지션 일괄 조회
        java.util.Set<UUID> memberIds = members.stream()
                .map(Member::getMemberId)
                .collect(java.util.stream.Collectors.toSet());

        java.util.Map<UUID, MemberPosition> positionByMember = memberPositionRepository
                .findActivePositionsByMemberIds(memberIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        mp -> mp.getMember().getMemberId(),
                        mp -> mp,
                        (a, b) -> a));

        // 3 직원 매핑 활성 포지션 있으면 부서명 포함 없으면 기본 변환
        return members.stream()
                .map(m -> {
                    MemberPosition mp = positionByMember.get(m.getMemberId());
                    return mp != null
                            ? MemberResDto.fromEntity(m, mp)
                            : MemberResDto.fromEntity(m);
                })
                .toList();
    }

    /**
     * 특정 대상자에 대한 평가자 후보 ID 배치 조회 (Feign 호출용).
     *
     * evalType 기준:
     *  - SELF: [target] 단일 반환
     *  - DOWNWARD: 같은 조직 내 JobGrade.displayOrder 가 target 보다 작은(= 상급자) 멤버
     *  - UPWARD:   같은 조직 내 JobGrade.displayOrder 가 target 보다 큰(= 하급자) 멤버
     *  - PEER:     같은 조직 내 JobGrade.displayOrder 가 target 과 같은(= 동급) 멤버, 본인 제외
     *
     * 조직이나 포지션 정보가 없는 대상자는 빈 배열을 반환한다.
     */
    @Transactional(readOnly = true)
    public java.util.List<UUID> findCandidatesForEvaluator(UUID targetMemberId, String evalType, UUID companyId) {
        if ("SELF".equalsIgnoreCase(evalType)) {
            return java.util.List.of(targetMemberId);
        }

        Member target = memberRepository.findById(targetMemberId).orElse(null);
        if (target == null) return java.util.Collections.emptyList();
        if (target.getDefaultPositionId() == null) return java.util.Collections.emptyList();

        MemberPosition targetPos = memberPositionRepository.findByIdWithDetails(target.getDefaultPositionId()).orElse(null);
        if (targetPos == null || targetPos.getOrganization() == null || targetPos.getJobGrade() == null) {
            return java.util.Collections.emptyList();
        }

        UUID orgId = targetPos.getOrganization().getOrganizationId();
        Integer targetRank = targetPos.getJobGrade().getDisplayOrder();
        if (targetRank == null) return java.util.Collections.emptyList();

        // 같은 회사 내 활성 사원 중 같은 조직에 속한 사람을 후보로 모음
        java.util.List<Member> sameCompany = memberRepository.findAllActiveByCompanyId(companyId, MemberStatus.ACTIVE);
        java.util.List<UUID> candidates = new java.util.ArrayList<>();
        java.util.List<UUID> leaderCandidates = new java.util.ArrayList<>();
        for (Member m : sameCompany) {
            if (m.getMemberId().equals(targetMemberId)) continue;
            if (m.getDefaultPositionId() == null) continue;
            java.util.Optional<MemberPosition> pOpt = memberPositionRepository.findByIdWithDetails(m.getDefaultPositionId());
            if (pOpt.isEmpty()) continue;
            MemberPosition p = pOpt.get();
            if (p.getOrganization() == null || !orgId.equals(p.getOrganization().getOrganizationId())) continue;
            if (p.getJobGrade() == null || p.getJobGrade().getDisplayOrder() == null) continue;
            int order = p.getJobGrade().getDisplayOrder();
            boolean match;
            switch (evalType.toUpperCase()) {
                case "DOWNWARD" -> {
                    if (isLeaderPosition(p)) {
                        leaderCandidates.add(m.getMemberId());
                    }
                    match = order < targetRank;
                }
                case "UPWARD"   -> match = order > targetRank;
                case "PEER"     -> match = order == targetRank;
                default -> match = false;
            }
            if (match) candidates.add(m.getMemberId());
        }
        if ("DOWNWARD".equalsIgnoreCase(evalType) && !leaderCandidates.isEmpty()) {
            return leaderCandidates;
        }
        return candidates;
    }

    private boolean isLeaderPosition(MemberPosition position) {
        if (position == null || position.getJobTitle() == null || position.getJobTitle().getName() == null) {
            return false;
        }
        String title = position.getJobTitle().getName().trim().toLowerCase();
        return title.contains("팀장")
                || title.contains("파트장")
                || title.contains("리더")
                || title.contains("lead")
                || title.contains("leader")
                || title.contains("manager");
    }

    /**
     * memberId → {name, department, position, profileUrl} 최소 프로필 배치 조회 (Feign 호출용).
     * 존재하지 않는 id 는 결과 Map 에 포함되지 않는다.
     * defaultPositionId 가 있는 경우 해당 포지션의 조직/직책 이름까지 채운다.
     */
    @Transactional(readOnly = true)
    public Map<UUID, MemberMinimalProfileResDto> findMinimalProfilesByIds(Collection<UUID> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) return Collections.emptyMap();
        Map<UUID, MemberMinimalProfileResDto> result = new HashMap<>();
        List<Member> members = memberRepository.findAllById(memberIds);
        for (Member m : members) {
            String department = null;
            String positionName = null;
            if (m.getDefaultPositionId() != null) {
                Optional<MemberPosition> posOpt = memberPositionRepository.findByIdWithDetails(m.getDefaultPositionId());
                if (posOpt.isPresent()) {
                    MemberPosition p = posOpt.get();
                    if (p.getOrganization() != null) {
                        department = p.getOrganization().getName();
                    }
                    if (p.getJobTitle() != null) {
                        positionName = p.getJobTitle().getName();
                    }
                }
            }
            result.put(m.getMemberId(),
                    MemberMinimalProfileResDto.builder()
                            .name(m.getName())
                            .department(department)
                            .positionName(positionName)
                            .profileUrl(m.getProfileUrl())
                            .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public MemberOrgContextResDto findOrgContext(UUID memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 직원입니다."));
        if (member.getDefaultPositionId() == null) {
            return null;
        }
        MemberPosition position = memberPositionRepository.findActiveByIdWithDetails(member.getDefaultPositionId())
                .orElse(null);
        if (position == null || position.getOrganization() == null) {
            return null;
        }
        return MemberOrgContextResDto.fromEntity(member, position);
    }

    @Transactional(readOnly = true)
    public java.util.Map<UUID, MemberOrgContextResDto> findOrgContextsByMemberIds(java.util.Collection<UUID> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        java.util.Map<UUID, MemberOrgContextResDto> result = new java.util.HashMap<>();
        java.util.List<MemberPosition> positions = memberPositionRepository.findActivePositionsByMemberIds(memberIds);
        for (MemberPosition position : positions) {
            if (position.getMember() == null || position.getOrganization() == null) {
                continue;
            }
            result.put(position.getMember().getMemberId(), MemberOrgContextResDto.fromEntity(position.getMember(), position));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public java.util.List<UUID> findActiveMemberIdsByOrganization(UUID organizationId) {
        return memberPositionRepository.findByOrganization_OrganizationIdAndDelYn(organizationId, "NO").stream()
                .filter(position -> "YES".equals(position.getIsActiveYn()))
                .map(MemberPosition::getMember)
                .filter(java.util.Objects::nonNull)
                .filter(member -> member.getMemberStatus() == MemberStatus.ACTIVE)
                .map(Member::getMemberId)
                .distinct()
                .toList();
    }


    @Transactional(readOnly = true)
    public DashboardProfileResDto getDashboardProfile(UUID memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."));

        MemberPosition position = memberPositionRepository
                .findById(member.getDefaultPositionId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "직위 정보가 없습니다."));

        // 오늘 일정 count
        LocalDateTime startAt = LocalDate.now().atStartOfDay();
        LocalDateTime endAt = LocalDate.now().atTime(23, 59, 59);
        int todayEventCount = calendarEventRepository
                .countTodayEvents(memberId, startAt, endAt);

        return DashboardProfileResDto.builder()
                .memberId(member.getMemberId())
                .name(member.getName())
                .profileUrl(member.getProfileUrl())
                .organizationName(position.getOrganization().getName())
                .jobGradeName(position.getJobGrade().getName())
                .jobTitleName(position.getJobTitle().getName())
                .todayEventCount(todayEventCount)
                .build();
    }

    // 서명 업로드
    public void uploadSignature(UUID memberId, MultipartFile signatureImage) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));

        // 기존 서명 있으면 S3 삭제
        if (member.getSignatureUrl() != null) {
            s3Uploader.delete(member.getSignatureUrl());
        }

        String url = s3Uploader.upload(signatureImage, "signature");
        member.updateSignatureUrl(url);
    }

    // 서명 조회
    public String getSignatureUrl(UUID memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
        return member.getSignatureUrl();
    }

    // 서명 삭제
    public void deleteSignature(UUID memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));

        if (member.getSignatureUrl() != null) {
            s3Uploader.delete(member.getSignatureUrl());
        }
        member.deleteSignatureUrl();
    }
    public void completeOnboarding(UUID memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."));
        member.completeOnboarding();
    }

    public MemberContractInfoResDto getMemberContractInfo(UUID memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "직원을 찾을 수 없습니다."));

        // 현재 포지션에서 부서/직책 가져오기
        String orgName = null;
        String jobTitleName = null;
        String jobGradeName = null;

        List<MemberPosition> positions = memberPositionRepository
                .findActivePositionsByMemberIds(List.of(memberId));

        if (!positions.isEmpty()) {
            MemberPosition position = positions.get(0);
            if (position.getOrganization() != null) {
                orgName = position.getOrganization().getName();
            }
            if (position.getJobTitle() != null) {
                jobTitleName = position.getJobTitle().getName();
            }
            if (position.getJobGrade() != null) {
                jobGradeName = position.getJobGrade().getName();
            }
        }

        return MemberContractInfoResDto.builder()
                .memberId(member.getMemberId())
                .name(member.getName())
                .sabun(member.getSabun())
                .organizationName(orgName)
                .jobTitleName(jobTitleName)
                .jobGradeName(jobGradeName)
                .build();
    }
}

