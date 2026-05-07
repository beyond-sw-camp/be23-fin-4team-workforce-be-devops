package com._team._team.contract.dto.reqdto;

import com._team._team.contract.domain.enums.ContractType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ContractTemplateCreateReqDto {
    @NotBlank(message = "템플릿 이름은 필수입니다.")
    private String templateName;

    @NotNull(message = "계약 유형은 필수입니다.")
    private ContractType contractType;

    @NotBlank(message = "양식 스키마는 필수입니다.")
    private String formSchema;
}
