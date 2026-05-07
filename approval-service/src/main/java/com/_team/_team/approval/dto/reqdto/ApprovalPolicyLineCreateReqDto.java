package com._team._team.approval.dto.reqdto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ApprovalPolicyLineCreateReqDto {

    @NotNull(message = "문서 ID는 필수입니다.")
    private UUID documentId;

    @NotNull(message = "결재라인 목록은 필수입니다.")
    @Valid
    private List<PolicyLineItem> policyLines;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class PolicyLineItem {

        @NotNull(message = "직책 ID는 필수입니다.")
        private UUID jobTitleId;

        @NotNull(message = "결재 순서는 필수입니다.")
        private Integer stepOrder;

        private UUID organizationId; // 없으면 요청자 조직 계열에서 검색
    }
}
