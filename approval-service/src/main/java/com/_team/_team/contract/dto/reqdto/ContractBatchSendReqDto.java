package com._team._team.contract.dto.reqdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ContractBatchSendReqDto {
    @NotNull(message = "템플릿 ID는 필수입니다.")
    private UUID templateId;

    @NotBlank(message = "배치 이름은 필수입니다.")
    private String batchName;

    @NotEmpty(message = "대상 직원 목록은 필수입니다.")
    private List<BatchItem> items;

    @Getter
    @NoArgsConstructor
    public static class BatchItem {
        @NotNull(message = "직원 ID는 필수입니다.")
        private UUID employeeMemberId;

        // 직원별 ADMIN_INPUT (예: 개별 신규연봉)
        private Map<String, Object> adminInputJson;
    }
}
