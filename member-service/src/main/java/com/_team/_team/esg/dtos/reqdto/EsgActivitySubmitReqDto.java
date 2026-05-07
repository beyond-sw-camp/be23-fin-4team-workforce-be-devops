package com._team._team.esg.dtos.reqdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EsgActivitySubmitReqDto {

    @NotNull(message = "활동 양식은 필수입니다.")
    private UUID subjectId;

    private String verificationContent;

    private MultipartFile image;
}