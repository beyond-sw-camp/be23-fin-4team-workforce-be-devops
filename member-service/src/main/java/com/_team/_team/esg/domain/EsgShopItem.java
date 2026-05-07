package com._team._team.esg.domain;

import com._team._team.company.domain.Company;
import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "esg_shop_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EsgShopItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID esgShopItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id",
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
            nullable = false)
    private Company company;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String imageUrl;

    @Column(nullable = false)
    private int requiredPoints;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    @Builder.Default
    private String delYn = "NO";

    public void update(String title, String description,
                       String imageUrl, int requiredPoints, int stock) {
        this.title          = title;
        this.description    = description;
        this.imageUrl       = imageUrl;
        this.requiredPoints = requiredPoints;
        this.stock          = stock;
    }

    public void decreaseStock() {
        if (this.stock <= 0) throw new IllegalStateException("재고가 없습니다.");
        this.stock--;
    }

    public void delete() { this.delYn = "YES"; }
}