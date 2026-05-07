package com._team._team.esg.dtos.reqdto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EsgShopItemCreateReqDto {

    @NotBlank(message = "물품명은 필수입니다.")
    private String title;

    private String description;

    @Positive(message = "필요 포인트는 0보다 커야 합니다.")
    private int requiredPoints;

    @Positive(message = "재고는 0보다 커야 합니다.")
    private int stock;

    private MultipartFile image;
}