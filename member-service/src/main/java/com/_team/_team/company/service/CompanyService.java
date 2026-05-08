package com._team._team.company.service;

import com._team._team.company.domain.Company;
import com._team._team.company.dto.reqdto.CompanyOnboardingReqDto;
import com._team._team.company.dto.resdto.CompanyInfoResDto;
import com._team._team.company.feignclients.ApprovalServiceClient;
import com._team._team.company.feignclients.SalaryServiceClient;
import com._team._team.company.repository.CompanyRepository;
import com._team._team.dto.BusinessException;
import com._team._team.esg.service.EsgService;
import com._team._team.member.domain.Member;
import com._team._team.member.repository.MemberRepository;
import com._team._team.member.service.MemberService;
import com._team._team.organization.service.OrganizationService;
import com._team._team.s3.S3Uploader;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import com._team._team.company.feignclients.AiSyncClient;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@Transactional
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final OrganizationService organizationService;
    private final MemberService memberService;
    private final NtsApiService ntsApiService;
    private final MailService mailService;
    private final RedisTemplate<String, String> emailRedisTemplate;
    private final MemberRepository memberRepository;
    private final S3Uploader s3Uploader;
    private final EsgService esgService;
    private final ApprovalServiceClient approvalServiceClient;
    private final SalaryServiceClient salaryServiceClient;
    private final AiSyncClient aiSyncClient;
    @Autowired
    public CompanyService(
            CompanyRepository companyRepository,
            OrganizationService organizationService,
            MemberService memberService,
            NtsApiService ntsApiService,
            MailService mailService,
            @Qualifier("emailInventory") RedisTemplate<String, String> emailRedisTemplate, MemberRepository memberRepository, S3Uploader s3Uploader, EsgService esgService, ApprovalServiceClient approvalServiceClient, SalaryServiceClient salaryServiceClient, AiSyncClient aiSyncClient) {
        this.companyRepository = companyRepository;
        this.organizationService = organizationService;
        this.memberService = memberService;
        this.ntsApiService = ntsApiService;
        this.mailService = mailService;
        this.emailRedisTemplate = emailRedisTemplate;
        this.memberRepository = memberRepository;
        this.s3Uploader = s3Uploader;
        this.esgService = esgService;
        this.approvalServiceClient = approvalServiceClient;
        this.salaryServiceClient = salaryServiceClient;
        this.aiSyncClient = aiSyncClient;
    }

    // 이메일 인증 코드 발송
    public void sendVerificationCode(String email) {

        // 이메일 중복 체크
        if (memberService.existsByEmail(email)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "이미 사용중인 이메일입니다."
            );
        }

        // 6자리 인증 코드 생성
        String code = generateVerificationCode();

        // Redis DB1에 저장 (5분)
        emailRedisTemplate.opsForValue().set(
                "EMAIL_VERIFY:" + email,
                code,
                5,
                TimeUnit.MINUTES
        );

        log.info("이메일 인증 코드 발송: {} → {}", email, code);

        // 메일 발송
        mailService.sendVerificationCode(email, code);
    }

    // 이메일 인증 코드 확인
    public void verifyCode(String email, String code) {

        String savedCode = emailRedisTemplate.opsForValue().get("EMAIL_VERIFY:" + email);

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
                "EMAIL_VERIFIED:" + email,
                "YES",
                30,
                TimeUnit.MINUTES
        );

        // 인증 코드 삭제
        emailRedisTemplate.delete("EMAIL_VERIFY:" + email);
    }

    // 회사 온보딩
    public void onboarding(CompanyOnboardingReqDto reqDto) {

//        // 1. 사업자번호 검증 (국세청 API) - 로컬 테스트용 주석처리
//        ntsApiService.validate(reqDto.getBusinessNumber());
//
//        // 2. 사업자번호 중복 체크
//        if (companyRepository.existsByBusinessNumber(reqDto.getBusinessNumber())) {
//            throw new BusinessException(
//                    HttpStatus.CONFLICT, "이미 등록된 사업자번호입니다."
//            );
//        }
//
//        // 3. 이메일 인증 여부 확인 - 로컬 테스트용 주석처리
//         String verified = emailRedisTemplate.opsForValue()
//                 .get("EMAIL_VERIFIED:" + reqDto.getAdminEmail());
//         if (!"YES".equals(verified)) {
//             throw new BusinessException(
//                     HttpStatus.BAD_REQUEST, "이메일 인증이 필요합니다."
//             );
//         }

        // 4. 비밀번호 확인
        if (!reqDto.getAdminPassword().equals(reqDto.getAdminPasswordCheck())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."
            );
        }

        // 5. 회사 생성
        Company company = createCompany(reqDto);

        // 6. 기본 조직/직급/직책 생성
        organizationService.createDefaultOrganization(company);
        organizationService.createDefaultJobGrade(company);
        organizationService.createDefaultJobTitle(company);

        // 7. 기본 역할/권한 생성
        memberService.createDefaultRoles(company);

        // 8. 관리자 계정 생성
        memberService.createAdminMember(reqDto, company);

        // 9. ESG 기본 config 생성 (전체 기능 OFF 상태로 초기화)
        esgService.createDefaultConfig(company);

        // 10. 인증 정보 삭제 - 로컬 테스트용 주석처리
         emailRedisTemplate.delete("EMAIL_VERIFIED:" + reqDto.getAdminEmail());

        // 기본 결재양식 생성 (approval-service 호출)
        try {
            approvalServiceClient.initDefaultDocuments(company.getCompanyId());
        } catch (Exception e) {
            log.error("기본 결재양식 생성 실패 companyId={}", company.getCompanyId(), e);
            if (e instanceof FeignException fe) {
                log.error("응답 body: {}", fe.contentUTF8());
            }
        }

        // 기본 계약서 템플릿 생성 (approval-service 호출)
        try {
            approvalServiceClient.initDefaultContractTemplates(company.getCompanyId());
        } catch (Exception e) {
            log.info("기본 계약서 템플릿 생성 실패: {}", e.getMessage());
            if (e instanceof FeignException fe) {
                log.error("응답 body: {}", fe.contentUTF8());
            }
        }

        // 기본 휴가 종류 생성 (salary-service 호출)
        try {
            salaryServiceClient.initDefaultLeaveTypes(company.getCompanyId());
        } catch (Exception e) {
            log.error("기본 휴가 생성 실패 companyId={}", company.getCompanyId(), e);
        }

        // 법정 공휴일 복사 (salary-service <- member-service Feign 내부 호출)
        try {
            salaryServiceClient.importPublicHolidays(company.getCompanyId());
        } catch (Exception e) {
            log.warn("법정 공휴일 가져오기 실패: {}", e.getMessage());
        }

        // 회사별 자동(스케줄러) 작업 트리거 시드 (salary-service)
        try {
            salaryServiceClient.initBatchSchedule(company.getCompanyId());
        } catch (Exception e) {
            log.warn("회사별 자동 작업 트리거 시드 실패: {}", e.getMessage());
        }
        // ai-service RAG 동기화 (Kafka 이벤트와 별개의 안전망 - 동기 호출)
        UUID companyId = company.getCompanyId();
        try {
            aiSyncClient.syncApproval(companyId);
        } catch (Exception e) {
            log.warn("AI 결재 양식 동기화 실패 companyId={}: {}", companyId, e.getMessage());
        }
        try {
            aiSyncClient.syncLeave(companyId);
        } catch (Exception e) {
            log.warn("AI 휴가 동기화 실패 companyId={}: {}", companyId, e.getMessage());
        }
        try {
            aiSyncClient.syncAttendance(companyId);
        } catch (Exception e) {
            log.warn("AI 근무 동기화 실패 companyId={}: {}", companyId, e.getMessage());
        }
        try {
            aiSyncClient.syncSalary(companyId);
        } catch (Exception e) {
            log.warn("AI 급여 동기화 실패 companyId={}: {}", companyId, e.getMessage());
        }

    }

    private Company createCompany(CompanyOnboardingReqDto reqDto) {
        Company company = Company.builder()
                .companyName(reqDto.getCompanyName())
                .companyDomain(reqDto.getCompanyDomain())
                .ceoName(reqDto.getCeoName())
                .businessNumber(reqDto.getBusinessNumber())
                .address(reqDto.getAddress())
                .detailAddress(reqDto.getDetailAddress())
                .build();
        return companyRepository.save(company);
    }

    public void updateLogo(UUID memberId, MultipartFile logo) {

        // 1. 요청자 조회
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."
                ));

        // 2. 회사 조회
        Company company = member.getCompany();

        // 3. 기존 로고 있으면 삭제
        if (company.getLogoUrl() != null) {
            s3Uploader.delete(company.getLogoUrl());
        }

        // 4. 새 로고 업로드
        String logoUrl = s3Uploader.upload(logo, "logo");

        // 5. 로고 URL 업데이트
        company.updateLogoUrl(logoUrl);
    }

    private String generateVerificationCode() {
        SecureRandom random = new SecureRandom();
        int code = random.nextInt(900000) + 100000;
        return String.valueOf(code);
    }
    @Transactional(readOnly = true)
    public CompanyInfoResDto getCompanyInfo(UUID memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."));
        return CompanyInfoResDto.fromEntity(member.getCompany());
    }

    public void updateSeal(UUID memberId, MultipartFile seal) {
        // memberId로 회사 조회 (기존 updateLogo와 동일한 방식)
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));

        Company company = member.getCompany();

        // 기존 직인 삭제
        if (company.getSealImageUrl() != null) {
            s3Uploader.delete(company.getSealImageUrl());
        }

        String sealUrl = s3Uploader.upload(seal, "company/" + company.getCompanyId() + "/seal");
        company.updateSealImageUrl(sealUrl);
    }

    public String getSealImageUrl(UUID companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "회사를 찾을 수 없습니다."));
        return company.getSealImageUrl();
    }

    /**
     * 회사 도메인으로 회사 단건 조회 - 시드용
     */
    public CompanyInfoResDto findByDomain(String domain) {
        Company company = companyRepository.findByCompanyDomain(domain)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "도메인으로 회사를 찾을 수 없습니다: " + domain));
        return CompanyInfoResDto.fromEntity(company);
    }
}