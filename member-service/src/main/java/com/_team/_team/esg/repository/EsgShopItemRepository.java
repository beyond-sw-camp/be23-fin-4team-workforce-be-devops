package com._team._team.esg.repository;

import com._team._team.company.domain.Company;
import com._team._team.esg.domain.EsgShopItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EsgShopItemRepository extends JpaRepository<EsgShopItem, UUID> {

    List<EsgShopItem> findByCompanyAndDelYn(Company company, String delYn);

    Optional<EsgShopItem> findByEsgShopItemIdAndDelYn(UUID esgShopItemId, String delYn);
}