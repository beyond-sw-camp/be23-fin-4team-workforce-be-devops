package com._team._team.member.dto.resdto;

import com._team._team.member.domain.Member;
import com._team._team.member.domain.MemberPosition;
import com._team._team.member.domain.enums.AccountStatus;
import com._team._team.member.domain.enums.EmploymentType;
import com._team._team.member.domain.enums.MemberStatus;
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
public class MemberDetailResDto {

    private UUID memberId;
    private String email;
    private String name;
    private String sabun;
    private LocalDate joinDate;
    private EmploymentType employmentType;
    private int esgScore;
    // 연락처
    private String phoneNumber;
    private String phonePublicYn;
    private String emergencyContact;

    // 주소
    private String address;
    private String detailAddress;
    private String addressPublicYn;

    // 급여
    private String bank;
    private String bankAccount;

    // 기타
    private String profileUrl;
    private String extensionNumber;
    private String telNumber;
    private UUID memberPositionId;

    // 조직 정보
    private String organizationName;
    private String jobGradeName;
    private String jobTitleName;

    //인사전용
    private MemberStatus memberStatus;
    private AccountStatus accountStatus;
    private String roleName;

    public static MemberDetailResDto fromEntity(Member member, MemberPosition position) {
        return MemberDetailResDto.builder()
                .memberId(member.getMemberId())
                .email(member.getEmail())
                .name(member.getName())
                .sabun(member.getSabun())
                .joinDate(member.getJoinDate())
                .employmentType(member.getEmploymentType())
                .profileUrl(member.getProfileUrl())
                .extensionNumber(member.getExtensionNumber())
                .telNumber(member.getTelNumber())
                .phoneNumber("YES".equals(member.getPhonePublicYn())
                        ? member.getPhoneNumber() : null)
                .phonePublicYn(member.getPhonePublicYn())
                .address("YES".equals(member.getAddressPublicYn())
                        ? member.getAddress() : null)
                .detailAddress("YES".equals(member.getAddressPublicYn())
                        ? member.getDetailAddress() : null)
                .addressPublicYn(member.getAddressPublicYn())
                .emergencyContact(null)
                .bank(null)
                .bankAccount(null)
                .organizationName(position.getOrganization().getName())
                .jobGradeName(position.getJobGrade().getName())
                .jobTitleName(position.getJobTitle().getName())
                .memberStatus(member.getMemberStatus())
                .accountStatus(member.getAccountStatus())
                .roleName(position.getRole() != null ? position.getRole().getName() : null)
                .memberPositionId(position.getMemberPositionId())
                .build();
    }

    public static MemberDetailResDto fromEntitySelf(Member member, MemberPosition position) {
        return MemberDetailResDto.builder()
                .memberId(member.getMemberId())
                .email(member.getEmail())
                .name(member.getName())
                .sabun(member.getSabun())
                .joinDate(member.getJoinDate())
                .employmentType(member.getEmploymentType())
                .profileUrl(member.getProfileUrl())
                .extensionNumber(member.getExtensionNumber())
                .telNumber(member.getTelNumber())
                .phoneNumber(member.getPhoneNumber())
                .phonePublicYn(member.getPhonePublicYn())
                .address(member.getAddress())
                .detailAddress(member.getDetailAddress())
                .addressPublicYn(member.getAddressPublicYn())
                .emergencyContact(member.getEmergencyContact())
                .bank(member.getBank())
                .bankAccount(member.getBankAccount())
                .organizationName(position.getOrganization().getName())
                .jobGradeName(position.getJobGrade().getName())
                .jobTitleName(position.getJobTitle().getName())
                .esgScore(member.getEsgScore())
                .memberPositionId(position.getMemberPositionId())
                .build();
    }
}