package com._team._team.member.domain;

import com._team._team.company.domain.Company;
import com._team._team.domain.BaseTimeEntity;
import com._team._team.member.domain.enums.AccountStatus;
import com._team._team.member.domain.enums.EmploymentType;
import com._team._team.member.domain.enums.MemberStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
            nullable = false)
    private Company company;

    @Column
    private UUID defaultPositionId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(unique = true)
    private String personalEmail;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    private String phoneNumber;

    @Column(nullable = false)
    @Builder.Default
    private String phonePublicYn = "NO";

    private String emergencyContact;

    private String address;

    private String detailAddress;

    @Column(nullable = false)
    @Builder.Default
    private String addressPublicYn = "NO";

    private String bank;

    private String bankAccount;

    private String profileUrl;

    private String sabun;

    private LocalDate joinDate;

    private String extensionNumber;

    private String telNumber;

    private String signatureUrl; //


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus memberStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmploymentType employmentType;

    /** 계약 만료일 - 계약직일 때 */
    @Column
    private LocalDate contractEndDate;

    @Column(nullable = false)
    @Builder.Default
    private Integer loginFailCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private String isFirstLoginYn = "YES";

    @Column(nullable = false)
    @Builder.Default
    private String isOnboardingYn = "YES";

    @Column(nullable = false)
    @Builder.Default
    private String isEmailVerifiedYn = "NO";

    @Column
    private LocalDate retireDate;  // 퇴직일 (사직서 승인 시 자동 설정)

    @Column(nullable = false)
    @Builder.Default
    private String delYn = "NO";

    @Column(nullable = false)
    @Builder.Default
    private int esgScore = 0;

    // 비즈니스 메서드
    public void updateDefaultPosition(UUID defaultPositionId) {
        this.defaultPositionId = defaultPositionId;
    }

    public void increaseLoginFailCount() {
        this.loginFailCount++;
        if (this.loginFailCount >= 5) {
            this.accountStatus = AccountStatus.BLOCKED;
        }
    }

    public void resetLoginFailCount() {
        this.loginFailCount = 0;
    }

    public void verifyEmail() {
        this.isEmailVerifiedYn = "YES";
    }

    public void completeFirstLogin() {
        this.isFirstLoginYn = "NO";
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void unblock() {
        this.accountStatus = AccountStatus.ACTIVE;
        this.loginFailCount = 0;
    }

    public void updateMemberStatus(MemberStatus status) {
        this.memberStatus = status;
    }

    public void delete() {
        this.delYn = "YES";
    }

    // restore 없으면 추가
    public void restore() {
        this.delYn = "NO";
    }
    public void updateProfileUrl(String profileUrl) {
        this.profileUrl = profileUrl;
    }
    // 기본 정보 수정 (인사팀)
    public void updateBasicInfo(String name, String sabun, LocalDate joinDate,
                                EmploymentType employmentType, MemberStatus memberStatus) {
        this.name = name;
        this.sabun = sabun;
        this.joinDate = joinDate;
        this.employmentType = employmentType;
        this.memberStatus = memberStatus;
    }

    // 마이페이지 수정 (본인)
    public void updateMyInfo(String phoneNumber, String phonePublicYn,
                             String emergencyContact, String address,
                             String detailAddress, String addressPublicYn,
                             String bank, String bankAccount,
                             String extensionNumber,
                             String telNumber) {
        if (phoneNumber != null) this.phoneNumber = phoneNumber;
        if (phonePublicYn != null) this.phonePublicYn = phonePublicYn;
        if (emergencyContact != null) this.emergencyContact = emergencyContact;
        if (address != null) this.address = address;
        if (detailAddress != null) this.detailAddress = detailAddress;
        if (addressPublicYn != null) this.addressPublicYn = addressPublicYn;
        if (bank != null) this.bank = bank;
        if (bankAccount != null) this.bankAccount = bankAccount;
        if (extensionNumber != null) this.extensionNumber = extensionNumber;
        if (telNumber != null) this.telNumber = telNumber;
    }

    public void updateEsgScore(int score) {
        this.esgScore = score;
    }

    public void updateSignatureUrl(String signatureUrl) {
        this.signatureUrl = signatureUrl;
    }

    public void deleteSignatureUrl() {
        this.signatureUrl = null;
    }

    public void completeOnboarding() {
        this.isOnboardingYn = "NO";
    }

    // 퇴직 메서드 추가
    public void markResigned(LocalDate retireDate) {
        this.memberStatus = MemberStatus.LEAVE;
        this.retireDate = retireDate;
    }

}