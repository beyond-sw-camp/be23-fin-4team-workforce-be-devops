package com._team._team.esg.dtos.reqdto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EsgRejectReqDto {

    @NotBlank(message = "반려 사유는 필수입니다.")
    private String reason;
}