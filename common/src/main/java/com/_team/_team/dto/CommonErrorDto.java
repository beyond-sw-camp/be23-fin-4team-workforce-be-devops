package com._team._team.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CommonErrorDto {

    private int status_code;
    private String error_message;

}
