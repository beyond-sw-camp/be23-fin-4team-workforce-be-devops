package com._team._team.contract.dto.reqdto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ContractSendReqDto {
    @NotNull(message = "템플릿 ID는 필수입니다.")
    private UUID templateId;

    @NotNull(message = "직원 ID는 필수입니다.")
    private UUID employeeMemberId;

    // ADMIN_INPUT 필드 값 (신규연봉, 특약사항 등)
    private Map<String, Object> adminInputJson;
}
