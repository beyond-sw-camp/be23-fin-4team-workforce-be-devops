package com._team._team.search.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.search.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final SearchService searchService;

    @Autowired
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/employees")
    public ResponseEntity<?> searchEmployees(
            @RequestParam String query,
            @RequestHeader("X-User-CompanyId") UUID companyId,
            Pageable pageable) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        searchService.searchEmployees(query, companyId, pageable),
                        "직원 검색 성공"),
                HttpStatus.OK
        );
    }

    @GetMapping("/organization")
    public ResponseEntity<?> getOrganizationTree(
            @RequestHeader("X-User-CompanyId") UUID companyId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        searchService.getOrganizationTree(companyId),
                        "조직 검색 성공"),
                HttpStatus.OK
        );
    }

    @GetMapping("/employees/organization")
    public ResponseEntity<?> searchEmployeesByOrganization(
            @RequestParam UUID organizationId,
            @RequestHeader("X-User-CompanyId") UUID companyId,
            Pageable pageable) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        searchService.searchEmployeesByOrganization(
                                organizationId, companyId, pageable),
                        "조직별 직원 검색 성공"),
                HttpStatus.OK
        );
    }
    // 1. 회사 전체 결재 검색
    @GetMapping("/approvals")
    public ResponseEntity<?> searchApprovals(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String requestType,
            @RequestHeader("X-User-CompanyId") UUID companyId,
            Pageable pageable) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        searchService.searchApprovals(query, companyId, status, requestType, pageable),
                        "결재 검색 성공"),
                HttpStatus.OK
        );
    }

    // 2. 내 기안 문서함 검색
    @GetMapping("/approvals/my-requests")
    public ResponseEntity<?> searchMyRequests(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String requestType,
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            Pageable pageable) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        searchService.searchMyRequests(
                                query, companyId, memberId, status, requestType, pageable),
                        "내 기안 문서 검색 성공"),
                HttpStatus.OK
        );
    }

    // 3. 부서 문서함 검색
    @GetMapping("/approvals/department")
    public ResponseEntity<?> searchDepartmentRequests(
            @RequestParam UUID organizationId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String requestType,
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            Pageable pageable) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        searchService.searchDepartmentRequests(
                                query, companyId, memberId, organizationId, status, requestType, pageable),
                        "부서 문서 검색 성공"),
                HttpStatus.OK
        );
    }

}