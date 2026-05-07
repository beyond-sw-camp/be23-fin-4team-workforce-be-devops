package com._team._team.esg.repository;
import com._team._team.company.domain.Company;
import com._team._team.esg.domain.EsgShopOrder;
import com._team._team.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EsgShopOrderRepository extends JpaRepository<EsgShopOrder, UUID> {

    List<EsgShopOrder> findByMemberOrderByCreatedAtDesc(Member member);

    @Query("SELECT o FROM EsgShopOrder o JOIN FETCH o.shopItem " +
            "WHERE o.shopItem.company = :company " +
            "ORDER BY o.createdAt DESC")
    List<EsgShopOrder> findByCompanyOrderByCreatedAtDesc(@Param("company") Company company);
}