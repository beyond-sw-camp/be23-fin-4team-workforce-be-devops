package com._team._team.approval.dto.reqdto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class CancelReqDto {

    @NotBlank(message = "취소 사유는 필수입니다.")
    private String cancelReason;
}
