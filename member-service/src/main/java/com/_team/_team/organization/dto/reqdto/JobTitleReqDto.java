package com._team._team.organization.dto.reqdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobTitleReqDto {

    @NotBlank(message = "직책명은 필수입니다.")
    private String name;

    @NotNull
    private int displayOrder;
}
