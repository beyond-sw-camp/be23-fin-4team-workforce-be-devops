package com._team._team.member.controller;

import com._team._team.annotation.Action;
import com._team._team.annotation.CheckPermission;
import com._team._team.annotation.Resource;
import com._team._team.dto.ApiResponse;
import com._team._team.member.dto.reqdto.*;
import com._team._team.member.dto.resdto.*;
import com._team._team.member.service.MemberService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/member")
public class MemberController {

    final MemberService memberService;

    @Autowired
    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }


    // 로그인
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginReqDto reqDto,
            HttpServletResponse response) {
        LoginResDto resDto = memberService.login(reqDto, response);
        return new ResponseEntity<>(
                ApiResponse.success(resDto, "로그인 성공"),
                HttpStatus.OK
        );
    }
    // 최초 로그인 비밀번호 변경
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestHeader("X-User-UUID") UUID memberId,
            @Valid @RequestBody ChangePasswordReqDto reqDto) {
        memberService.changePassword(memberId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(null, "비밀번호 변경 성공"),
                HttpStatus.OK
        );
    }

    // 로그아웃
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestHeader("X-User-UUID") UUID memberId,
            HttpServletResponse response) {
        memberService.logout(memberId, response);
        return new ResponseEntity<>(
                ApiResponse.success(null, "로그아웃 성공"),
                HttpStatus.OK
        );
    }

    // AT 재발급
    @PostMapping("/generate-at")
    public ResponseEntity<?> generateAt(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId,
            HttpServletRequest request) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        memberService.generateAt(memberId, memberPositionId, request),
                        "AT 재발급 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 본인 권한 목록 조회.
     * - JWT(AT)에는 권한이 없어 새로고침/AT 연장 후 FE 권한 캐시(Me.permissions) 재수화 용도로 사용.
     * - {@code @CheckPermission} 미적용 — 인증된 사용자라면 자기 권한은 항상 조회 가능해야 한다
     *   (예: 팀장은 ROLE:READ 권한이 없어 GET /member/role/{roleId} 가 403 이라 기존 보강이 실패함).
     */
    @GetMapping("/me/permissions")
    public ResponseEntity<?> getMyPermissions(
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        memberService.getMyPermissions(memberPositionId),
                        "내 권한 조회 성공"),
                HttpStatus.OK
        );
    }

    // 직원 계정 생성
    @CheckPermission(resource = Resource.MEMBER, action = Action.CREATE)
    @PostMapping("/create")
    public ResponseEntity<?> createMember(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId,
            @Valid @RequestBody MemberCreateReqDto reqDto) {
        memberService.createMember(memberId, memberPositionId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(null, "직원 계정 생성 성공"),
                HttpStatus.CREATED
        );
    }

    // 직원 목록 조회
    @CheckPermission(resource = Resource.MEMBER, action = Action.READ)
    @GetMapping("/list")
    public ResponseEntity<?> getMemberList(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId) {
        return new ResponseEntity<>(
                ApiResponse.success(memberService.getMemberList(memberId, memberPositionId), "직원 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    // 직원 검색 (QueryDSL 동적쿼리 + 페이징)
    @CheckPermission(resource = Resource.MEMBER, action = Action.READ)
    @GetMapping("/search")
    public ResponseEntity<?> searchMembers(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId,
            @ModelAttribute MemberSearchCondition condition,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<MemberSearchItemResDto> result = memberService.searchMembers(memberId, condition, pageable);
        return new ResponseEntity<>(
                ApiResponse.success(result, "직원 검색 성공"),
                HttpStatus.OK
        );
    }

    // 직원 상세 조회
    @CheckPermission(resource = Resource.MEMBER, action = Action.READ)
    @GetMapping("/detail/{targetMemberId}")
    public ResponseEntity<?> getMemberDetail(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId,
            @PathVariable UUID targetMemberId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        memberService.getMemberDetail(memberId, memberPositionId, targetMemberId),
                        "직원 상세 조회 성공"),
                HttpStatus.OK
        );
    }
    // 비밀번호 재설정 인증 코드 발송
    @PostMapping("/reset-password/send-code")
    public ResponseEntity<?> sendResetPasswordCode(
            @RequestParam String personalEmail) {
        memberService.sendResetPasswordCode(personalEmail);
        return new ResponseEntity<>(
                ApiResponse.success(null, "인증 코드가 발송됐습니다."),
                HttpStatus.OK
        );
    }

    // 비밀번호 재설정 인증 코드 확인
    @PostMapping("/reset-password/verify-code")
    public ResponseEntity<?> verifyResetPasswordCode(
            @RequestParam String personalEmail,
            @RequestParam String code) {
        memberService.verifyResetPasswordCode(personalEmail, code);
        return new ResponseEntity<>(
                ApiResponse.success(null, "인증이 완료됐습니다."),
                HttpStatus.OK
        );
    }

    // 비밀번호 재설정
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @Valid @RequestBody ResetPasswordReqDto reqDto) {
        memberService.resetPassword(reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(null, "비밀번호가 재설정됐습니다."),
                HttpStatus.OK
        );
    }

    // 직원 수정 (인사팀)
    @CheckPermission(resource = Resource.MEMBER, action = Action.UPDATE)
    @PutMapping("/update/{targetMemberId}")
    public ResponseEntity<?> updateMember(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestHeader("X-User-MemberPositionId") UUID memberPositionId,
            @PathVariable UUID targetMemberId,
            @Valid @RequestBody MemberUpdateReqDto reqDto) {
        memberService.updateMember(memberId, memberPositionId, targetMemberId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(null, "직원 정보 수정 성공"),
                HttpStatus.OK
        );
    }

    // 마이페이지 수정 (본인)
    @PutMapping("/my-info")
    public ResponseEntity<?> updateMyInfo(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestBody MyInfoUpdateReqDto reqDto) {
        memberService.updateMyInfo(memberId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(null, "내 정보 수정 성공"),
                HttpStatus.OK
        );
    }
    // 직원 삭제
    @CheckPermission(resource = Resource.MEMBER, action = Action.DELETE)
    @DeleteMapping("/{targetMemberId}")
    public ResponseEntity<?> deleteMember(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID targetMemberId) {
        memberService.deleteMember(memberId, targetMemberId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "직원 삭제 성공"),
                HttpStatus.OK
        );
    }

    // 직원 복원
    @CheckPermission(resource = Resource.MEMBER, action = Action.DELETE)
    @PatchMapping("/{targetMemberId}/restore")
    public ResponseEntity<?> restoreMember(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID targetMemberId) {
        memberService.restoreMember(memberId, targetMemberId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "직원 복원 성공"),
                HttpStatus.OK
        );
    }
    //역할 변경
    @CheckPermission(resource = Resource.ROLE, action = Action.UPDATE)
    @PutMapping("/update/{targetMemberId}/role")
    public ResponseEntity<?> updateMemberRole(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID targetMemberId,
            @RequestBody UpdateMemberRoleReqDto reqDto) {
        memberService.updateMemberRole(memberId, targetMemberId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(null, "역할 변경 성공"),
                HttpStatus.OK
        );
    }
    // 직원 이력 조회
    @CheckPermission(resource = Resource.MEMBER, action = Action.READ)
    @GetMapping("/{targetMemberId}/history")
    public ResponseEntity<?> getMemberHistory(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID targetMemberId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        memberService.getMemberHistory(memberId, targetMemberId),
                        "직원 이력 조회 성공"),
                HttpStatus.OK
        );
    }

    // 역할 생성
    @CheckPermission(resource = Resource.ROLE, action = Action.CREATE)
    @PostMapping("/role/create")
    public ResponseEntity<?> createRole(
            @RequestHeader("X-User-UUID") UUID memberId,
            @Valid @RequestBody RoleReqDto reqDto) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        memberService.createRole(memberId, reqDto),
                        "역할 생성 성공"),
                HttpStatus.CREATED
        );
    }

    // 역할 목록 조회
    @CheckPermission(resource = Resource.ROLE, action = Action.READ)
    @GetMapping("/role/list")
    public ResponseEntity<?> getRoleList(
            @RequestHeader("X-User-UUID") UUID memberId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        memberService.getRoleList(memberId),
                        "역할 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    // 역할 상세 조회
    @CheckPermission(resource = Resource.ROLE, action = Action.READ)
    @GetMapping("/role/{roleId}")
    public ResponseEntity<?> getRoleDetail(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID roleId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        memberService.getRoleDetail(memberId, roleId),
                        "역할 상세 조회 성공"),
                HttpStatus.OK
        );
    }

    // 역할 수정
    @CheckPermission(resource = Resource.ROLE, action = Action.UPDATE)
    @PutMapping("/role/{roleId}")
    public ResponseEntity<?> updateRole(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID roleId,
            @Valid @RequestBody RoleReqDto reqDto) {
        memberService.updateRole(memberId, roleId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(null, "역할 수정 성공"),
                HttpStatus.OK
        );
    }

    // 역할 삭제
    @CheckPermission(resource = Resource.ROLE, action = Action.DELETE)
    @DeleteMapping("/role/{roleId}")
    public ResponseEntity<?> deleteRole(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID roleId) {
        memberService.deleteRole(memberId, roleId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "역할 삭제 성공"),
                HttpStatus.OK
        );
    }
    // 계정 잠금 해제
    @CheckPermission(resource = Resource.MEMBER, action = Action.UPDATE)
    @PatchMapping("/{targetMemberId}/unblock")
    public ResponseEntity<?> unblockMember(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID targetMemberId) {
        memberService.unblockMember(memberId, targetMemberId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "계정 잠금 해제 성공"),
                HttpStatus.OK
        );
    }
    // 프로필 이미지 업로드
    @PatchMapping("/profile-image")
    public ResponseEntity<?> updateProfileImage(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestParam("profileImage") MultipartFile profileImage) {
        memberService.updateProfileImage(memberId, profileImage);
        return new ResponseEntity<>(
                ApiResponse.success(null, "프로필 이미지 업로드 성공"),
                HttpStatus.OK
        );
    }

    // 프로필 이미지 삭제
    @DeleteMapping("/profile-image")
    public ResponseEntity<?> deleteProfileImage(
            @RequestHeader("X-User-UUID") UUID memberId) {
        memberService.deleteProfileImage(memberId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "프로필 이미지 삭제 성공"),
                HttpStatus.OK
        );
    }
    // 휴직 처리
    @CheckPermission(resource = Resource.MEMBER, action = Action.UPDATE)
    @PatchMapping("/{targetMemberId}/dormant")
    public ResponseEntity<?> dormantMember(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID targetMemberId) {
        memberService.dormantMember(memberId, targetMemberId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "휴직 처리 성공"),
                HttpStatus.OK
        );
    }

    // 복직 처리
    @CheckPermission(resource = Resource.MEMBER, action = Action.UPDATE)
    @PatchMapping("/{targetMemberId}/return")
    public ResponseEntity<?> returnMember(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID targetMemberId) {
        memberService.returnMember(memberId, targetMemberId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "복직 처리 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 회사별 재직 사원 전체 조회
     */
    @GetMapping("/internal/by-company")
    public ResponseEntity<?> findAllByCompanyIdForBatch(
            @RequestHeader("X-User-CompanyId") UUID companyId) {
        List<MemberResDto> result = memberService.findAllByCompanyIdForBatch(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "재직 사원 조회 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 회사별 시스템 관리자 (인사/대표 등) 조회 - 알림 발송 대상
     * - MemberPosition.isSystemAdminYn = 'YES' 인 활성 직원
     */
    @GetMapping("/internal/admins-by-company")
    public ResponseEntity<?> findAdminsByCompany(
            @RequestParam UUID companyId) {
        List<MemberResDto> result = memberService.findAdminsByCompanyId(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "회사 관리자 조회 성공"),
                HttpStatus.OK
        );
    }

    // 대시보드용 내 프로필
    @GetMapping("/dashboard-profile")
    public ResponseEntity<?> getDashboardProfile(
            @RequestHeader("X-User-UUID") UUID memberId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        memberService.getDashboardProfile(memberId),
                        "대시보드 프로필 조회 성공"),
                HttpStatus.OK
        );
    }

    // 서명 이미지 업로드
    @PatchMapping("/signature")
    public ResponseEntity<?> uploadSignature(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestParam("signatureImage") MultipartFile signatureImage) {
        memberService.uploadSignature(memberId, signatureImage);
        return new ResponseEntity<>(
                ApiResponse.success(null, "서명 이미지 업로드 성공"),
                HttpStatus.OK
        );
    }

    // 서명 이미지 조회
    @GetMapping("/signature")
    public ResponseEntity<?> getSignature(
            @RequestHeader("X-User-UUID") UUID memberId) {
        String signatureUrl = memberService.getSignatureUrl(memberId);
        return new ResponseEntity<>(
                ApiResponse.success(signatureUrl, "서명 이미지 조회 성공"),
                HttpStatus.OK
        );
    }

    // 서명 이미지 삭제
    @DeleteMapping("/signature")
    public ResponseEntity<?> deleteSignature(
            @RequestHeader("X-User-UUID") UUID memberId) {
        memberService.deleteSignature(memberId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "서명 이미지 삭제 성공"),
                HttpStatus.OK
        );
    }

    // 내부용: 서명 URL 조회 (Feign 호출용)
    @GetMapping("/internal/{memberId}/signature")
    public SignatureResDto getSignatureInternal(@PathVariable UUID memberId) {
        String signatureUrl = memberService.getSignatureUrl(memberId);
        return new SignatureResDto(signatureUrl);
    }

    /**
     * 내부용: memberId 목록에 대한 최소 프로필(name/department/position/profileUrl) 배치 조회.
     * 평가 화면의 Avatar/이름/부서 노출용.
     *
     * 응답 형식: { "<memberId>": { name, department, positionName, profileUrl } }
     */
    @GetMapping("/internal/profiles")
    public java.util.Map<java.util.UUID, MemberMinimalProfileResDto>
    getProfilesInternal(@RequestParam("ids") java.util.List<java.util.UUID> ids) {
        return memberService.findMinimalProfilesByIds(ids);
    }

    /**
     * 내부용: 특정 대상자에 대한 평가자 후보 조회 (Feign 호출용).
     * evalType ∈ {SELF, DOWNWARD, UPWARD, PEER} — 조직 + 직급(JobGrade.displayOrder) 기반.
     *
     * 응답: [memberId, ...]
     */
    @GetMapping("/internal/{memberId}/org-context")
    public MemberOrgContextResDto getOrgContextInternal(@PathVariable UUID memberId) {
        return memberService.findOrgContext(memberId);
    }

    @GetMapping("/internal/org-contexts")
    public java.util.Map<java.util.UUID, MemberOrgContextResDto> getOrgContextsInternal(
            @RequestParam("ids") java.util.List<java.util.UUID> ids) {
        return memberService.findOrgContextsByMemberIds(ids);
    }

    @GetMapping("/internal/organizations/{organizationId}/member-ids")
    public java.util.List<java.util.UUID> getActiveMemberIdsByOrganizationInternal(
            @PathVariable UUID organizationId) {
        return memberService.findActiveMemberIdsByOrganization(organizationId);
    }

    @GetMapping("/internal/candidates-for-evaluator")
    public java.util.List<java.util.UUID> getCandidatesForEvaluator(
            @RequestHeader("X-User-CompanyId") String companyId,
            @RequestParam("targetMemberId") java.util.UUID targetMemberId,
            @RequestParam("evalType") String evalType) {
        return memberService.findCandidatesForEvaluator(
                targetMemberId, evalType, java.util.UUID.fromString(companyId));
    }

    @PatchMapping("/onboarding/complete")
    public ResponseEntity<?> completeOnboarding(
            @RequestHeader("X-User-UUID") UUID memberId) {
        memberService.completeOnboarding(memberId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "온보딩 완료"),
                HttpStatus.OK
        );
    }

    /** 내부용: 직원 기본정보 조회 (Feign 호출용 - 전자계약 등) */
    @GetMapping("/internal/{memberId}/info")
    public MemberContractInfoResDto getMemberInfoInternal(@PathVariable UUID memberId) {
        return memberService.getMemberContractInfo(memberId);
    }


}
