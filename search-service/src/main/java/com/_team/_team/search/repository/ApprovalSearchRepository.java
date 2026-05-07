package com._team._team.search.repository;

import com._team._team.search.domain.ApprovalDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ApprovalSearchRepository
        extends ElasticsearchRepository<ApprovalDocument, String> {
}