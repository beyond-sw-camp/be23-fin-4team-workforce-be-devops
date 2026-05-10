package com._team._team.organization.controller;

import com._team._team.annotation.Action;
import com._team._team.annotation.CheckPermission;
import com._team._team.annotation.Resource;
import com._team._team.dto.ApiResponse;
import com._team._team.organization.dto.reqdto.JobGradeReqDto;
import com._team._team.organization.dto.reqdto.JobTitleReqDto;
import com._team._team.organization.dto.reqdto.OrganizationReqDto;
import com._team._team.organization.dto.resdto.JobGradeResDto;
import com._team._team.organization.dto.resdto.OrganizationResDto;
import com._team._team.organization.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/organization")
public class OrganizationController {

    private final OrganizationService organizationService;

    @Autowired
    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @CheckPermission(resource = Resource.ORGANIZATION, action = Action.CREATE)
    @PostMapping("/create")
    public ResponseEntity<?> createOrganization(
            @RequestHeader("X-User-UUID") UUID memberId,
            @Valid @RequestBody OrganizationReqDto reqDto) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        organizationService.createOrganization(memberId, reqDto),
                        "조직 생성 성공"),
                HttpStatus.CREATED
        );
    }

    /** 본인 소속 회사 조직 트리 (서비스에서 member → company 로 한정) */
    @GetMapping("/list")
    public ResponseEntity<?> getOrganizationList(
            @RequestHeader("X-User-UUID") UUID memberId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        organizationService.getOrganizationList(memberId),
                        "조직 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    @CheckPermission(resource = Resource.ORGANIZATION, action = Action.UPDATE)
    @PutMapping("/{organizationId}")
    public ResponseEntity<?> updateOrganization(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID organizationId,
            @Valid @RequestBody OrganizationReqDto reqDto) {
        organizationService.updateOrganization(memberId, organizationId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(null, "조직 수정 성공"),
                HttpStatus.OK
        );
    }

    @CheckPermission(resource = Resource.ORGANIZATION, action = Action.DELETE)
    @DeleteMapping("/{organizationId}")
    public ResponseEntity<?> deleteOrganization(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID organizationId) {
        organizationService.deleteOrganization(memberId, organizationId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "조직 삭제 성공"),
                HttpStatus.OK
        );
    }
    // 직급 생성
    @CheckPermission(resource = Resource.ORGANIZATION, action = Action.CREATE)
    @PostMapping("/job-grade/create")
    public ResponseEntity<?> createJobGrade(
            @RequestHeader("X-User-UUID") UUID memberId,
            @Valid @RequestBody JobGradeReqDto reqDto) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        organizationService.createJobGrade(memberId, reqDto),
                        "직급 생성 성공"),
                HttpStatus.CREATED
        );
    }

    // 직급 목록 조회
    @CheckPermission(resource = Resource.ORGANIZATION, action = Action.READ)
    @GetMapping("/job-grade/list")
    public ResponseEntity<?> getJobGradeList(
            @RequestHeader("X-User-UUID") UUID memberId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        organizationService.getJobGradeList(memberId),
                        "직급 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    // 직급 수정
    @CheckPermission(resource = Resource.ORGANIZATION, action = Action.UPDATE)
    @PutMapping("/job-grade/{jobGradeId}")
    public ResponseEntity<?> updateJobGrade(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID jobGradeId,
            @Valid @RequestBody JobGradeReqDto reqDto) {
        organizationService.updateJobGrade(memberId, jobGradeId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(null, "직급 수정 성공"),
                HttpStatus.OK
        );
    }

    // 직급 삭제
    @CheckPermission(resource = Resource.ORGANIZATION, action = Action.DELETE)
    @DeleteMapping("/job-grade/{jobGradeId}")
    public ResponseEntity<?> deleteJobGrade(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID jobGradeId) {
        organizationService.deleteJobGrade(memberId, jobGradeId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "직급 삭제 성공"),
                HttpStatus.OK
        );
    }

    // 직급 순서 변경
    @CheckPermission(resource = Resource.ORGANIZATION, action = Action.UPDATE)
    @PutMapping("/job-grade/reorder")
    public ResponseEntity<?> reorderJobGrade(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestBody List<UUID> jobGradeIdList) {
        organizationService.reorderJobGrade(memberId, jobGradeIdList);
        return new ResponseEntity<>(
                ApiResponse.success(null, "직급 순서 변경 성공"),
                HttpStatus.OK
        );
    }

    // 직책 생성
    @CheckPermission(resource = Resource.ORGANIZATION, action = Action.CREATE)
    @PostMapping("/job-title/create")
    public ResponseEntity<?> createJobTitle(
            @RequestHeader("X-User-UUID") UUID memberId,
            @Valid @RequestBody JobTitleReqDto reqDto) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        organizationService.createJobTitle(memberId, reqDto),
                        "직책 생성 성공"),
                HttpStatus.CREATED
        );
    }

    // 직책 목록 조회
    @CheckPermission(resource = Resource.ORGANIZATION, action = Action.READ)
    @GetMapping("/job-title/list")
    public ResponseEntity<?> getJobTitleList(
            @RequestHeader("X-User-UUID") UUID memberId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        organizationService.getJobTitleList(memberId),
                        "직책 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    // 직책 수정
    @CheckPermission(resource = Resource.ORGANIZATION, action = Action.UPDATE)
    @PutMapping("/job-title/{jobTitleId}")
    public ResponseEntity<?> updateJobTitle(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID jobTitleId,
            @Valid @RequestBody JobTitleReqDto reqDto) {
        organizationService.updateJobTitle(memberId, jobTitleId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(null, "직책 수정 성공"),
                HttpStatus.OK
        );
    }

    // 직책 삭제
    @CheckPermission(resource = Resource.ORGANIZATION, action = Action.DELETE)
    @DeleteMapping("/job-title/{jobTitleId}")
    public ResponseEntity<?> deleteJobTitle(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID jobTitleId) {
        organizationService.deleteJobTitle(memberId, jobTitleId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "직책 삭제 성공"),
                HttpStatus.OK
        );
    }

    // 직책 순서 변경
    @CheckPermission(resource = Resource.ORGANIZATION, action = Action.UPDATE)
    @PutMapping("/job-title/reorder")
    public ResponseEntity<?> reorderJobTitle(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestBody List<UUID> jobTitleIdList) {
        organizationService.reorderJobTitle(memberId, jobTitleIdList);
        return new ResponseEntity<>(
                ApiResponse.success(null, "직책 순서 변경 성공"),
                HttpStatus.OK
        );
    }
    //조직 순서 변경
    @CheckPermission(resource = Resource.ORGANIZATION, action = Action.UPDATE)
    @PutMapping("/reorder")
    public ResponseEntity<?> reorderOrganization(
            @RequestBody List<UUID> organizationIdList) {
        organizationService.reorderOrganization(organizationIdList);
        return new ResponseEntity<>(
                ApiResponse.success(null, "조직 순서 변경 성공"),
                HttpStatus.OK
        );
    }
    /**
     * 조직도 조회
     * - 회사 전체 조직 트리
     * - 조직별 직급별 직원 목록
     */
    @GetMapping("/org-chart")
    public ResponseEntity<?> getOrgChart(
            @RequestHeader("X-User-UUID") UUID memberId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        organizationService.getOrgChart(memberId),
                        "조직도 조회 성공"),
                HttpStatus.OK
        );
    }

    // 조직 단건 조회 ///
    @GetMapping("/organization/{organizationId}")
    public ResponseEntity<?> findById(@PathVariable UUID organizationId) {
        OrganizationResDto result = organizationService.findById(organizationId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "조직 조회 성공"),
                HttpStatus.OK
        );
    }

    @GetMapping("/internal/{organizationId}")
    public OrganizationResDto findByIdInternal(@PathVariable UUID organizationId) {
        return organizationService.findById(organizationId);
    }
    // 캘린더 팀 필터용 조직 목록 조회
    @GetMapping("/simple-list")
    public ResponseEntity<?> getOrganizationSimpleList(
            @RequestHeader("X-User-UUID") UUID memberId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        organizationService.getOrganizationList(memberId),
                        "조직 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    @GetMapping("/internal/{organizationId}/descendants")
    public List<UUID> findDescendantIdsInternal(@PathVariable UUID organizationId) {
        return organizationService.findDescendantIds(organizationId);
    }

    // 회사별 직급 목록
    @GetMapping("/internal/job-grade/by-company")
    public List<JobGradeResDto> getJobGradeListByCompanyInternal(
            @RequestParam("companyId") UUID companyId) {
        return organizationService.getJobGradeListByCompany(companyId);
    }
}
