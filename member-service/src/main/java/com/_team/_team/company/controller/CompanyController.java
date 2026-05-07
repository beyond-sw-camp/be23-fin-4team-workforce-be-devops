package com._team._team.company.controller;

import com._team._team.company.dto.reqdto.CompanyOnboardingReqDto;
import com._team._team.company.service.CompanyService;
import com._team._team.company.service.NtsApiService;
import com._team._team.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/company")
public class CompanyController {

    private final CompanyService companyService;
    private final NtsApiService ntsApiService;

    @Autowired
    public CompanyController(CompanyService companyService, NtsApiService ntsApiService) {
        this.companyService = companyService;
        this.ntsApiService = ntsApiService;
    }

    // 사업자번호 검증
    @GetMapping("/check-business-number")
    public ResponseEntity<?> checkBusinessNumber(
            @RequestParam String businessNumber) {
        ntsApiService.validate(businessNumber);
        return new ResponseEntity<>(
                ApiResponse.success(null, "유효한 사업자번호입니다."),
                HttpStatus.OK
        );
    }

    // 이메일 인증 코드 발송
    @PostMapping("/send-verification-code")
    public ResponseEntity<?> sendVerificationCode(
            @RequestParam String email) {
        companyService.sendVerificationCode(email);
        return new ResponseEntity<>(
                ApiResponse.success(null, "인증 코드가 발송됐습니다."),
                HttpStatus.OK
        );
    }

    // 이메일 인증 코드 확인
    @PostMapping("/verify-code")
    public ResponseEntity<?> verifyCode(
            @RequestParam String email,
            @RequestParam String code) {
        companyService.verifyCode(email, code);
        return new ResponseEntity<>(
                ApiResponse.success(null, "이메일 인증이 완료됐습니다."),
                HttpStatus.OK
        );
    }

    // 회사 온보딩
    @PostMapping("/onboarding")
    public ResponseEntity<?> onboarding(
            @Valid @RequestBody CompanyOnboardingReqDto reqDto) {
        companyService.onboarding(reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(null, "온보딩이 완료됐습니다."),
                HttpStatus.CREATED
        );
    }
    // 회사 로고 업로드
    @PatchMapping("/logo")
    public ResponseEntity<?> updateLogo(
            @RequestHeader("X-User-Id") UUID memberId,
            @RequestPart MultipartFile logo) {
        companyService.updateLogo(memberId, logo);
        return new ResponseEntity<>(
                ApiResponse.success(null, "로고 업로드 성공"),
                HttpStatus.OK
        );
    }
    // 회사 정보 조회
    @GetMapping("/info")
    public ResponseEntity<?> getCompanyInfo(
            @RequestHeader("X-User-UUID") UUID memberId) {
        return new ResponseEntity<>(
                ApiResponse.success(companyService.getCompanyInfo(memberId), "회사 정보 조회 성공"),
                HttpStatus.OK
        );
    }

    // 회사 직인 업로드
    @PatchMapping("/seal")
    public ResponseEntity<?> updateSeal(
            @RequestHeader("X-User-Id") UUID memberId,
            @RequestPart MultipartFile seal) {
        companyService.updateSeal(memberId, seal);
        return new ResponseEntity<>(
                ApiResponse.success(null, "직인 업로드 성공"),
                HttpStatus.OK
        );
    }

    // 회사 직인 조회 (internal - 다른 서비스에서 Feign 호출용)
    @GetMapping("/internal/{companyId}/seal")
    public String getSealImageUrl(@PathVariable UUID companyId) {
        return companyService.getSealImageUrl(companyId);
    }

    /**
     * 회사 도메인으로 회사 단건 조회 - 시드용 (데모)
     */
    @GetMapping("/internal/by-domain/{domain}")
    public ResponseEntity<?> findByDomain(@PathVariable String domain) {
        return new ResponseEntity<>(
                ApiResponse.success(companyService.findByDomain(domain), "회사 조회 성공"),
                HttpStatus.OK);
    }
}