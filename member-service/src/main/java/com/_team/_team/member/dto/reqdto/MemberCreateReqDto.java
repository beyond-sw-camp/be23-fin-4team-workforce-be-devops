package com._team._team.member.dto.reqdto;

import com._team._team.company.domain.Company;
import com._team._team.member.domain.Member;
import com._team._team.member.domain.enums.AccountStatus;
import com._team._team.member.domain.enums.EmploymentType;
import com._team._team.member.domain.enums.MemberStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberCreateReqDto {

    // 필수 (인사팀 입력)
    @NotBlank(message = "이름을 입력해 주세요.")
    private String name;

    @NotBlank(message = "영문 이니셜을 입력해 주세요.")
    private String englishInitial;

    @NotBlank(message = "개인 이메일을 입력해 주세요.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String personalEmail;

    @NotNull(message = "입사일을 입력해 주세요.")
    private LocalDate joinDate;

    @NotNull(message = "고용형태를 선택해 주세요.")
    private EmploymentType employmentType;

    @NotNull(message = "조직을 선택해 주세요.")
    private UUID organizationId;

    @NotNull(message = "직급을 선택해 주세요.")
    private UUID jobGradeId;

    @NotNull(message = "직책을 선택해 주세요.")
    private UUID jobTitleId;

    @NotNull(message = "역할을 선택해 주세요.")
    private UUID roleId;

//    // 기본급 입사 시 HR이 협의된 금액 입력 Salary 자동 생성에 사용
//    @NotNull(message = "기본급을 입력해 주세요.")
//    @Min(value = 0, message = "기본급은 0원 이상이어야 합니다.")
//    private Long baseSalary;

    // 선택 (최초 로그인 후 직원 직접 입력)
    private String phoneNumber;
    private String emergencyContact;
    private String address;
    private String detailAddress;
    private String bank;
    private String bankAccount;
    private String profileUrl;
    private String extensionNumber;
    private String telNumber;

    public Member toEntity(String companyEmail, String encodedPassword, Company company,String sabun) {
        return Member.builder()
                .company(company)
                .email(companyEmail)
                .personalEmail(this.personalEmail)
                .password(encodedPassword)
                .name(this.name)
                .sabun(sabun)
                .joinDate(this.joinDate)
                .employmentType(this.employmentType)
                .phoneNumber(this.phoneNumber)
                .emergencyContact(this.emergencyContact)
                .address(this.address)
                .detailAddress(this.detailAddress)
                .bank(this.bank)
                .bankAccount(this.bankAccount)
                .profileUrl(this.profileUrl)
                .extensionNumber(this.extensionNumber)
                .telNumber(this.telNumber)
                .memberStatus(MemberStatus.ACTIVE)
                .accountStatus(AccountStatus.ACTIVE)
                .isFirstLoginYn("YES")
                .isEmailVerifiedYn("NO")
                .isOnboardingYn("NO")
                .build();
    }
}