package com._team._team.member.dto.reqdto;

import com._team._team.member.domain.enums.EmploymentType;
import com._team._team.member.domain.enums.MemberStatus;
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
public class MemberUpdateReqDto {

    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    @NotBlank(message = "사번은 필수입니다.")
    private String sabun;

    @NotNull(message = "입사일은 필수입니다.")
    private LocalDate joinDate;

    @NotNull(message = "고용형태는 필수입니다.")
    private EmploymentType employmentType;

    @NotNull(message = "재직상태는 필수입니다.")
    private MemberStatus memberStatus;

    @NotNull(message = "조직은 필수입니다.")
    private UUID organizationId;

    @NotNull(message = "직급은 필수입니다.")
    private UUID jobGradeId;

    @NotNull(message = "직책은 필수입니다.")
    private UUID jobTitleId;

    @NotNull(message = "역할은 필수입니다.")
    private UUID roleId;

    // 승진 여부 (GRADE_CHANGE vs PROMOTION 구분용)
    private Boolean isPromotion;

    // 변경 사유
    private String changeReason;
}