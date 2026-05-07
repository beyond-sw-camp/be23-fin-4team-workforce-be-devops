package com._team._team.contract.dto.reqdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ContractBatchResendReqDto {
    @NotBlank
    private String batchName;

    @NotEmpty
    private List<BatchResendItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchResendItem {
        private UUID contractId;
        private Map<String, Object> adminInputJson;
    }
}
