package com._team._team.member.dto.reqdto;

import com._team._team.member.domain.enums.EmploymentType;
import com._team._team.member.domain.enums.MemberStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class MemberSearchCondition {

    // 이름/사번/이메일 부분일치 (한 입력으로 세 컬럼 LIKE OR)
    private String keyword;

    // 회사 (보통 호출하는 사람 회사로 자동주입, 외부에서 받지말고 컨트롤러에서 채워넣기)
    private UUID companyId;

    // 부서 (조직)
    private UUID organizationId;

    // 직책/직급
    private UUID jobTitleId;
    private UUID jobGradeId;

    // 재직상태/고용형태
    private MemberStatus memberStatus;
    private EmploymentType employmentType;

    // 입사일 범위
    private LocalDate joinDateFrom;
    private LocalDate joinDateTo;

    // 삭제 포함 여부 (기본 false → delYn='NO'만 봄)
    @Builder.Default
    private boolean includeDeleted = false;
}
