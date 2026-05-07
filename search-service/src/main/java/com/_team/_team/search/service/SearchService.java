package com._team._team.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com._team._team.search.domain.MemberDocument;
import com._team._team.search.domain.OrganizationDocument;
import com._team._team.search.dto.resdto.MemberSearchResDto;
import com._team._team.search.repository.ApprovalSearchRepository;
import com._team._team.search.repository.MemberSearchRepository;
import com._team._team.search.repository.OrganizationSearchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitSupport;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.stereotype.Service;
import com._team._team.search.domain.ApprovalDocument;
import com._team._team.search.dto.resdto.ApprovalSearchResDto;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import java.util.function.Consumer;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class SearchService {

    private final MemberSearchRepository memberSearchRepository;
    private final OrganizationSearchRepository organizationSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient elasticsearchClient;
    private final ApprovalSearchRepository approvalSearchRepository;

    @Autowired
    public SearchService(MemberSearchRepository memberSearchRepository, OrganizationSearchRepository organizationSearchRepository, ElasticsearchOperations elasticsearchOperations, ElasticsearchClient elasticsearchClient, ApprovalSearchRepository approvalSearchRepository) {
        this.memberSearchRepository = memberSearchRepository;
        this.organizationSearchRepository = organizationSearchRepository;
        this.elasticsearchOperations = elasticsearchOperations;
        this.elasticsearchClient = elasticsearchClient;
        this.approvalSearchRepository = approvalSearchRepository;
    }

    // 직원 통합 검색
    public Page<MemberSearchResDto> searchEmployees(
            String query, UUID companyId, Pageable pageable) {

        Query searchQuery = buildEmployeeSearchQuery(
                query, companyId.toString(), pageable);

        SearchHits<MemberDocument> searchHits =
                elasticsearchOperations.search(
                        searchQuery, MemberDocument.class);

        return SearchHitSupport
                .searchPageFor(searchHits, pageable)
                .map(hit -> MemberSearchResDto
                        .fromDocument(hit.getContent()));
    }

    // 조직 트리 조회
    public List<OrganizationDocument> getOrganizationTree(UUID companyId) {
        return organizationSearchRepository
                .findByCompanyIdOrderByLabelAsc(companyId.toString());
    }

    // 조직별 직원 검색
    public Page<MemberSearchResDto> searchEmployeesByOrganization(
            UUID organizationId, UUID companyId, Pageable pageable) {

        Query searchQuery = new NativeQueryBuilder()
                .withQuery(q -> q.bool(b -> b
                        .must(s -> s.nested(n -> n
                                .path("organizationList")
                                .query(nq -> nq.term(t -> t
                                        .field("organizationList.organizationId")
                                        .value(organizationId.toString())))))
                        .filter(f -> f.term(t -> t
                                .field("companyId")
                                .value(companyId.toString())))))
                .withPageable(pageable)
                .build();

        SearchHits<MemberDocument> searchHits =
                elasticsearchOperations.search(
                        searchQuery, MemberDocument.class);

        return SearchHitSupport
                .searchPageFor(searchHits, pageable)
                .map(hit -> MemberSearchResDto
                        .fromDocument(hit.getContent()));
    }

    // 검색 쿼리 빌더
    private Query buildEmployeeSearchQuery(
            String query, String companyId, Pageable pageable) {

        if (query.matches("^[0-9\\-]+$")) {
            return new NativeQueryBuilder()
                    .withQuery(q -> q.bool(b -> b
                            .must(m -> m.term(t -> t
                                    .field("phoneNumber")
                                    .value(query)))
                            .filter(f -> f.term(t -> t
                                    .field("companyId")
                                    .value(companyId)))))
                    .withPageable(pageable)
                    .build();
        }

        return new NativeQueryBuilder()
                .withQuery(q -> q.bool(b -> b
                        .should(s -> s.multiMatch(mm -> mm
                                .query(query)
                                .fields("name", "titleName",
                                        "email", "position")))
                        .should(s -> s.nested(n -> n
                                .path("organizationList")
                                .query(nq -> nq.match(m -> m
                                        .field("organizationList.name")
                                        .query(query)))))
                        .minimumShouldMatch("1")
                        .filter(f -> f.term(t -> t
                                .field("companyId")
                                .value(companyId)))))
                .withPageable(pageable)
                .build();
    }



    private Query buildApprovalSearchQuery(
            String query, String companyId, String status, String requestType, Pageable pageable) {

        return new NativeQueryBuilder()
                .withQuery(q -> q.bool(b -> {
                    // companyId 필수 필터
                    b.filter(f -> f.term(t -> t
                            .field("companyId")
                            .value(companyId)));

                    // 상태 필터 (선택)
                    if (status != null && !status.isBlank()) {
                        b.filter(f -> f.term(t -> t
                                .field("requestStatus")
                                .value(status)));
                    }

                    // 타입 필터 (선택)
                    if (requestType != null && !requestType.isBlank()) {
                        b.filter(f -> f.term(t -> t
                                .field("requestType")
                                .value(requestType)));
                    }

                    // 검색어 (선택)
                    if (query != null && !query.isBlank()) {
                        b.must(m -> m.multiMatch(mm -> mm
                                .query(query)
                                .fields("documentName",
                                        "requesterName",
                                        "requesterOrganizationName",
                                        "contentJson")));
                    } else {
                        b.must(m -> m.matchAll(ma -> ma));
                    }

                    return b;
                }))
                .withPageable(pageable)
                .build();
    }
    // ========== 1. 회사 전체 결재 검색 ==========
    public Page<ApprovalSearchResDto> searchApprovals(
            String query, UUID companyId, String status, String requestType, Pageable pageable) {

        Query searchQuery = buildApprovalQuery(
                query, companyId, status, requestType,
                builder -> {},   // 추가 필터 없음
                pageable);

        return executeApprovalSearch(searchQuery, pageable);
    }

    // ========== 2. 내 기안 문서함 검색 ==========
    public Page<ApprovalSearchResDto> searchMyRequests(
            String query, UUID companyId, UUID memberId,
            String status, String requestType, Pageable pageable) {

        Query searchQuery = buildApprovalQuery(
                query, companyId, status, requestType,
                b -> {
                    // 기안자 == 본인
                    b.filter(f -> f.term(t -> t
                            .field("memberId")
                            .value(memberId.toString())));
                },
                pageable);

        return executeApprovalSearch(searchQuery, pageable);
    }

    // ========== 3. 부서 문서함 검색 ==========
    public Page<ApprovalSearchResDto> searchDepartmentRequests(
            String query, UUID companyId, UUID memberId, UUID organizationId,
            String status, String requestType, Pageable pageable) {

        Query searchQuery = buildApprovalQuery(
                query, companyId, status, requestType,
                b -> {
                    // 기안 부서 == 지정 부서
                    b.filter(f -> f.term(t -> t
                            .field("requesterOrganizationId")
                            .value(organizationId.toString())));

                    // (공개 문서) OR (본인 작성)
                    b.filter(f -> f.bool(bb -> bb
                            .should(s -> s.term(t -> t
                                    .field("isDeptVisibleYn")
                                    .value("Y")))
                            .should(s -> s.term(t -> t
                                    .field("memberId")
                                    .value(memberId.toString())))
                            .minimumShouldMatch("1")));
                },
                pageable);

        return executeApprovalSearch(searchQuery, pageable);
    }

// ==================== 공통 헬퍼 ====================

    /**
     * 결재 검색 공통 쿼리 빌더
     * - 회사 격리 (필수)
     * - 검색어, 상태, 타입 필터 (선택)
     * - 각 문서함별 추가 필터는 extraFilter 콜백으로 주입
     */
    private Query buildApprovalQuery(
            String query, UUID companyId, String status, String requestType,
            Consumer<BoolQuery.Builder> extraFilter,
            Pageable pageable) {

        return new NativeQueryBuilder()
                .withQuery(q -> q.bool(b -> {
                    // 회사 필터 (필수)
                    b.filter(f -> f.term(t -> t
                            .field("companyId")
                            .value(companyId.toString())));

                    // 상태 필터 (선택)
                    if (status != null && !status.isBlank()) {
                        b.filter(f -> f.term(t -> t
                                .field("requestStatus")
                                .value(status)));
                    }

                    // 타입 필터 (선택)
                    if (requestType != null && !requestType.isBlank()) {
                        b.filter(f -> f.term(t -> t
                                .field("requestType")
                                .value(requestType)));
                    }

                    // 검색어 (선택)
                    if (query != null && !query.isBlank()) {
                        b.must(m -> m.multiMatch(mm -> mm
                                .query(query)
                                .fields("documentName",
                                        "requesterName",
                                        "requesterOrganizationName",
                                        "contentJson")));
                    } else {
                        b.must(m -> m.matchAll(ma -> ma));
                    }

                    // 각 문서함별 추가 필터 적용
                    extraFilter.accept(b);

                    return b;
                }))
                .withPageable(pageable)
                .build();
    }

    /**
     * ES 검색 실행 + DTO 변환 공통 로직
     */
    private Page<ApprovalSearchResDto> executeApprovalSearch(Query searchQuery, Pageable pageable) {
        SearchHits<ApprovalDocument> searchHits =
                elasticsearchOperations.search(searchQuery, ApprovalDocument.class);

        return SearchHitSupport
                .searchPageFor(searchHits, pageable)
                .map(hit -> ApprovalSearchResDto.fromDocument(hit.getContent()));
    }
}