package com._team._team.search.repository;

import com._team._team.search.domain.OrganizationDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationSearchRepository
        extends ElasticsearchRepository<OrganizationDocument, String> {

    Optional<OrganizationDocument> findByCompanyIdAndLabel(
            String companyId, String label);

    List<OrganizationDocument> findByCompanyId(String companyId);

    List<OrganizationDocument> findByCompanyIdOrderByLabelAsc(
            String companyId);
}