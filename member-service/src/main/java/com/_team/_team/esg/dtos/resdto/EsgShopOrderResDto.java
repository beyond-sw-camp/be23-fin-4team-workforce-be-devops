package com._team._team.esg.dtos.resdto;

import com._team._team.esg.domain.EsgShopOrder;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EsgShopOrderResDto {

    private UUID esgShopOrderId;
    private UUID memberId;
    private String memberName;
    private UUID esgShopItemId;
    private String itemTitle;
    private String itemImageUrl;
    private int usedPoints;
    private LocalDateTime createdAt;

    public static EsgShopOrderResDto fromEntity(EsgShopOrder order) {
        return EsgShopOrderResDto.builder()
                .esgShopOrderId(order.getEsgShopOrderId())
                .memberId(order.getMember().getMemberId())
                .memberName(order.getMember().getName())
                .esgShopItemId(order.getShopItem().getEsgShopItemId())
                .itemTitle(order.getShopItem().getTitle())
                .itemImageUrl(order.getShopItem().getImageUrl())
                .usedPoints(order.getUsedPoints())
                .createdAt(order.getCreatedAt())
                .build();
    }
}