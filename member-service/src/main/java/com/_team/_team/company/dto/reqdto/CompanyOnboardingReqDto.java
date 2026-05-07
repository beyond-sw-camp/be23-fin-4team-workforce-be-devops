package com._team._team.company.dto.reqdto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyOnboardingReqDto {

    @NotBlank(message = "회사명은 필수입니다.")
    private String companyName;

    @NotBlank(message = "대표자명은 필수입니다.")
    private String ceoName;

    @NotBlank(message = "회사도메인은 필수입니다.")
    private String companyDomain;

    @NotBlank(message = "사업자번호는 필수입니다.")
    @Pattern(regexp = "\\d{10}", message = "사업자번호는 10자리 숫자입니다.")
    private String businessNumber;

    @NotBlank(message = "주소는 필수입니다.")
    private String address;

    private String detailAddress;

    @NotBlank(message = "관리자 이름은 필수입니다.")
    private String adminName;

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String adminEmail;

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String adminPassword;

    @NotBlank(message = "비밀번호 확인은 필수입니다.")
    private String adminPasswordCheck;
}