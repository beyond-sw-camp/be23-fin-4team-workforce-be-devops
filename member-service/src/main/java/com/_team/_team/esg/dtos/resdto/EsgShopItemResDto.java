package com._team._team.esg.dtos.resdto;

import com._team._team.esg.domain.EsgShopItem;
import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EsgShopItemResDto {

    private UUID esgShopItemId;
    private String title;
    private String description;
    private String imageUrl;
    private int requiredPoints;
    private int stock;

    public static EsgShopItemResDto fromEntity(EsgShopItem item) {
        return EsgShopItemResDto.builder()
                .esgShopItemId(item.getEsgShopItemId())
                .title(item.getTitle())
                .description(item.getDescription())
                .imageUrl(item.getImageUrl())
                .requiredPoints(item.getRequiredPoints())
                .stock(item.getStock())
                .build();
    }
}