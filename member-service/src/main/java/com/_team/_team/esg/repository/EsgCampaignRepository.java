package com._team._team.esg.repository;

import com._team._team.company.domain.Company;
import com._team._team.esg.domain.EsgCampaign;
import com._team._team.esg.domain.enums.CampaignStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EsgCampaignRepository extends JpaRepository<EsgCampaign, UUID> {

    List<EsgCampaign> findByCompanyOrderByCreatedAtDesc(Company company);

    List<EsgCampaign> findByCompanyAndStatusOrderByCreatedAtDesc(Company company, CampaignStatus status);

    Optional<EsgCampaign> findByEsgCampaignIdAndCompany(UUID esgCampaignId, Company company);
}