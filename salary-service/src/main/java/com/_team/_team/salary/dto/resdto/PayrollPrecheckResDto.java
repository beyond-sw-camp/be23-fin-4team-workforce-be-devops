package com._team._team.salary.dto.resdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 급여대장 생성 직전 가드 응답
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PayrollPrecheckResDto {

    /** 회사 재직(ACTIVE) 직원 수 */
    private int totalActiveMembers;

    private int missingSalaryCount;
    private List<MemberRef> missingSalary;

    private int missingBankAccountCount;
    private List<MemberRef> missingBankAccount;

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class MemberRef {
        private UUID memberId;
        private String name;
        private String sabun;
        private String organizationName;
    }
}
