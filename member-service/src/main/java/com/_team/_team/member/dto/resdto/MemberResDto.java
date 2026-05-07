package com._team._team.member.dto.resdto;

import com._team._team.member.domain.Member;
import com._team._team.member.domain.MemberPosition;
import com._team._team.member.domain.enums.AccountStatus;
import com._team._team.member.domain.enums.EmploymentType;
import com._team._team.member.domain.enums.MemberStatus;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberResDto {

    private UUID memberId;
    private String email;
    private String name;
    private String sabun;
    private LocalDate joinDate;
    private EmploymentType employmentType;
    private MemberStatus memberStatus;
    private AccountStatus accountStatus;
    private String profileUrl;
    private String extensionNumber;
    private String telNumber;

    // phonePublicYn = YES 일 때만 노출
    private String phoneNumber;

    // 포지션 정보
    private UUID memberPositionId;
    private String organizationName;
    private String jobGradeName;
    private String jobTitleName;
    private String roleName;

    public static MemberResDto fromEntity(Member member) {
        return MemberResDto.builder()
                .memberId(member.getMemberId())
                .email(member.getEmail())
                .name(member.getName())
                .sabun(member.getSabun())
                .joinDate(member.getJoinDate())
                .employmentType(member.getEmploymentType())
                .memberStatus(member.getMemberStatus())
                .accountStatus(member.getAccountStatus())
                .profileUrl(member.getProfileUrl())
                .extensionNumber(member.getExtensionNumber())
                .telNumber(member.getTelNumber())
                // phonePublicYn 확인 후 노출
                .phoneNumber("YES".equals(member.getPhonePublicYn())
                        ? member.getPhoneNumber() : null)
                .build();
    }

    public static MemberResDto fromEntity(Member member, MemberPosition position) {
        return MemberResDto.builder()
                .memberId(member.getMemberId())
                .email(member.getEmail())
                .name(member.getName())
                .sabun(member.getSabun())
                .joinDate(member.getJoinDate())
                .employmentType(member.getEmploymentType())
                .memberStatus(member.getMemberStatus())
                .accountStatus(member.getAccountStatus())
                .profileUrl(member.getProfileUrl())
                .extensionNumber(member.getExtensionNumber())
                .telNumber(member.getTelNumber())
                .phoneNumber("YES".equals(member.getPhonePublicYn())
                        ? member.getPhoneNumber() : null)
                .memberPositionId(position.getMemberPositionId())
                .organizationName(safeName(() -> position.getOrganization().getName()))
                .jobGradeName(safeName(() -> position.getJobGrade().getName()))
                .jobTitleName(safeName(() -> position.getJobTitle().getName()))
                .roleName(safeName(() -> position.getRole().getName()))
                .build();
    }

    private static String safeName(Supplier<String> nameSupplier) {
        try {
            return nameSupplier.get();
        } catch (EntityNotFoundException | NullPointerException e) {
            return null;
        }
    }
}