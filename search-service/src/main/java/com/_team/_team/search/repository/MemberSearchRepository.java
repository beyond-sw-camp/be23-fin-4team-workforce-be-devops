package com._team._team.search.repository;

import com._team._team.search.domain.MemberDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface MemberSearchRepository
        extends ElasticsearchRepository<MemberDocument, String> {
}
