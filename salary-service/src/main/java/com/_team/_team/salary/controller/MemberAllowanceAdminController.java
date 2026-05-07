package com._team._team.salary.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.salary.domain.MemberAllowance;
import com._team._team.salary.domain.enums.AllowanceApprovalStatus;
import com._team._team.salary.dto.reqdto.MemberAllowanceAutoGrantReqDto;
import com._team._team.salary.dto.resdto.MemberAllowanceResDto;
import com._team._team.salary.service.MemberAllowanceService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** 관리자 - 직원 수당 관리 컨트롤러 */
@RestController
@RequestMapping("/salary/admin/allowances")
public class MemberAllowanceAdminController {

    private final MemberAllowanceService memberAllowanceService;

    @Autowired
    public MemberAllowanceAdminController(MemberAllowanceService memberAllowanceService) {
        this.memberAllowanceService = memberAllowanceService;
    }

    /** 신규 입사자에게 기본 수당 AUTO 등록 (결재 생략) */
    @PostMapping("/auto-grant")
    public ResponseEntity<?> autoGrant(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @Valid @RequestBody MemberAllowanceAutoGrantReqDto reqDto) {
        MemberAllowance saved = memberAllowanceService.autoGrant(
                reqDto.getMemberId(), companyId,
                reqDto.getSalaryItemTemplateId(),
                reqDto.getAmount(),
                reqDto.getEffectiveFrom());
        return new ResponseEntity<>(
                ApiResponse.success(MemberAllowanceResDto.fromEntity(saved), "수당이 자동 부여되었습니다."),
                HttpStatus.CREATED
        );
    }

    /** 회사 전체 수당 목록 조회 (월 단위 활성, 상태 필터 지원) */
    @GetMapping
    public ResponseEntity<?> findByCompanyId(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestParam(required = false) AllowanceApprovalStatus status,
            @RequestParam(required = false) String yearMonth) {
        YearMonth ym = (yearMonth == null || yearMonth.isBlank())
                ? YearMonth.now()
                : YearMonth.parse(yearMonth);
        List<MemberAllowanceResDto> result = memberAllowanceService
                .findCompanyActiveInMonth(companyId, status, ym).stream()
                .map(MemberAllowanceResDto::fromEntity)
                .toList();
        return new ResponseEntity<>(
                ApiResponse.success(result, "회사 전체 수당 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    /**
     * orphan 수당 정리 - Salary 가 없는 직원의 잔여 수당을 일괄 소프트 삭제.
     * SalaryService.delete cascade 도입 이전 데이터 정리용.
     */
    @PostMapping("/cleanup-orphans")
    public ResponseEntity<?> cleanupOrphans(
            @RequestHeader("X-User-CompanyId") UUID companyId) {
        int closed = memberAllowanceService.cleanupOrphans(companyId);
        return new ResponseEntity<>(
                ApiResponse.success(closed, "orphan 수당 " + closed + "건 정리 완료"),
                HttpStatus.OK
        );
    }

    /**
     * 관리자가 특정 직원/템플릿의 활성 수당을 즉시 종료.
     * 급여 수정 시 부가 수당 토글에서 체크 해제된 항목을 한 건씩 끌 때 사용.
     */
    @PostMapping("/members/{memberId}/templates/{templateId}/close")
    public ResponseEntity<?> closeByTemplate(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID memberId,
            @PathVariable UUID templateId,
            @RequestParam(required = false) String closeAt) {
        java.time.LocalDate date = (closeAt == null || closeAt.isBlank())
                ? java.time.LocalDate.now()
                : java.time.LocalDate.parse(closeAt);
        int n = memberAllowanceService.adminCloseByTemplate(memberId, companyId, templateId, date);
        return new ResponseEntity<>(
                ApiResponse.success(n, n > 0 ? "수당 종료 처리됨" : "활성 수당 없음"),
                HttpStatus.OK
        );
    }

    /**
     * 관리자가 특정 직원/템플릿의 모든 활성 행을 일괄 소프트 삭제.
     * 미래 effectiveFrom 의 수당이라 close 가 아닌 hard-delete 가 적절할 때 사용.
     */
    @DeleteMapping("/members/{memberId}/templates/{templateId}")
    public ResponseEntity<?> deleteByTemplate(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID memberId,
            @PathVariable UUID templateId) {
        int n = memberAllowanceService.adminDeleteByTemplate(memberId, companyId, templateId);
        return new ResponseEntity<>(
                ApiResponse.success(n, n + "건 삭제 처리됨"),
                HttpStatus.OK
        );
    }

    /** 회사 전체 이력 (활성 + 종료, 효력일 역순) - 상세 모드용 */
    @GetMapping("/history")
    public ResponseEntity<?> findAllHistory(
            @RequestHeader("X-User-CompanyId") UUID companyId) {
        List<MemberAllowanceResDto> result = memberAllowanceService
                .findCompanyAllHistory(companyId).stream()
                .map(MemberAllowanceResDto::fromEntity)
                .toList();
        return new ResponseEntity<>(
                ApiResponse.success(result, "회사 수당 전체 이력 조회 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 단건 수당 종료 - 이력 보존
     */
    @PostMapping("/{memberAllowanceId}/close")
    public ResponseEntity<?> closeOne(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID memberAllowanceId,
            @RequestParam(required = false) String closeAt) {
        LocalDate at = (closeAt == null || closeAt.isBlank())
                ? LocalDate.now() : LocalDate.parse(closeAt);
        memberAllowanceService.adminCloseOne(memberAllowanceId, companyId, at);
        return new ResponseEntity<>(
                ApiResponse.success(null, "받는 수당이 해체되었습니다."),
                HttpStatus.OK
        );
    }

    /**
     * 단건 수당 소프트 삭제 (실수 정정용) - [완전 삭제].
     */
    @DeleteMapping("/{memberAllowanceId}")
    public ResponseEntity<?> deleteOne(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID memberAllowanceId) {
        memberAllowanceService.adminSoftDeleteOne(memberAllowanceId, companyId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "수당이 삭제되었습니다."),
                HttpStatus.OK
        );
    }

    /**
     * 특정 직원의 활성 수당 조회 (정산 미리보기).
     * yearMonth(YYYY-MM) 미지정 시 현재 월. date 가 들어오면 해당 월 기준 (구버전 호환).
     */
    @GetMapping("/members/{memberId}/active")
    public ResponseEntity<?> findActiveByMemberId(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @PathVariable UUID memberId,
            @RequestParam(required = false) String yearMonth,
            @RequestParam(required = false) LocalDate date) {
        YearMonth ym;
        if (yearMonth != null && !yearMonth.isBlank()) {
            ym = YearMonth.parse(yearMonth);
        } else if (date != null) {
            ym = YearMonth.from(date);
        } else {
            ym = YearMonth.now();
        }
        List<MemberAllowanceResDto> result = memberAllowanceService
                .findMemberActiveInMonth(memberId, companyId, ym).stream()
                .map(MemberAllowanceResDto::fromEntity)
                .toList();
        return new ResponseEntity<>(
                ApiResponse.success(result, "직원 활성 수당 조회 성공"),
                HttpStatus.OK
        );
    }
}
