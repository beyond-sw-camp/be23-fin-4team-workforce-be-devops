package com._team._team.esg.controller;

import com._team._team.annotation.Action;
import com._team._team.annotation.CheckPermission;
import com._team._team.annotation.Resource;
import com._team._team.dto.ApiResponse;
import com._team._team.esg.domain.enums.ActivityStatus;
import com._team._team.esg.dtos.reqdto.*;
import com._team._team.esg.service.EsgService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/esg")
public class EsgController {

    private final EsgService esgService;

    @Autowired
    public EsgController(EsgService esgService) {
        this.esgService = esgService;
    }

    // ═══════════════════════════════════════════════════════════
    // Config
    // ═══════════════════════════════════════════════════════════

    /**
     * ESG 그린장터 설정 조회
     * - esgEnabledYn ON/OFF 상태 및 월 포인트 한도 조회
     * - 모든 직원 접근 가능
     */
    @GetMapping("/config")
    public ResponseEntity<?> getConfig(
            @RequestHeader("X-User-UUID") UUID memberId) {
        return new ResponseEntity<>(
                ApiResponse.success(esgService.getConfig(memberId), "ESG 설정 조회 성공"),
                HttpStatus.OK
        );
    }

    /**
     * ESG 그린장터 설정 수정
     * - esgEnabledYn: ESG 전체 기능 ON/OFF (활동 인증 + 포인트 + 포인트샵 한번에)
     * - monthlyPointLimit: 월 포인트 적립 한도
     * - 권한: ESG UPDATE
     */
    @CheckPermission(resource = Resource.ESG, action = Action.UPDATE)
    @PutMapping("/config")
    public ResponseEntity<?> updateConfig(
            @RequestHeader("X-User-UUID") UUID memberId,
            @Valid @RequestBody EsgConfigUpdateReqDto reqDto) {
        esgService.updateConfig(memberId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(null, "ESG 설정 수정 성공"),
                HttpStatus.OK
        );
    }

    // ═══════════════════════════════════════════════════════════
    // ActivitySubject
    // ═══════════════════════════════════════════════════════════

    /**
     * 활동 양식 생성
     * - 회사별 커스텀 ESG 활동 양식 생성
     * - category: E(환경) / S(사회) / G(지배구조)
     * - defaultPoints: 해당 활동 승인 시 지급할 기본 포인트
     * - 권한: ESG CREATE
     */
    @CheckPermission(resource = Resource.ESG, action = Action.CREATE)
    @PostMapping("/subjects")
    public ResponseEntity<?> createSubject(
            @RequestHeader("X-User-UUID") UUID memberId,
            @Valid @RequestBody EsgSubjectCreateReqDto reqDto) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        esgService.createSubject(memberId, reqDto), "활동 양식 생성 성공"),
                HttpStatus.CREATED
        );
    }

    /**
     * 활동 양식 목록 조회
     * - 회사 소속 활동 양식 전체 조회 (delYn = NO)
     * - 직원이 활동 제출 시 양식 선택 목록으로 활용
     * - 모든 직원 접근 가능
     */
    @GetMapping("/subjects")
    public ResponseEntity<?> getSubjectList(
            @RequestHeader("X-User-UUID") UUID memberId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        esgService.getSubjectList(memberId), "활동 양식 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 활동 양식 수정
     * - 제목, 설명, 카테고리, 기본 포인트 수정 가능
     * - 다른 회사 양식 수정 불가
     * - 권한: ESG UPDATE
     */
    @CheckPermission(resource = Resource.ESG, action = Action.UPDATE)
    @PutMapping("/subjects/{subjectId}")
    public ResponseEntity<?> updateSubject(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID subjectId,
            @Valid @RequestBody EsgSubjectCreateReqDto reqDto) {
        esgService.updateSubject(memberId, subjectId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(null, "활동 양식 수정 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 활동 양식 삭제
     * - 소프트 삭제 (delYn = YES)
     * - 삭제된 양식으로 제출된 기존 활동 데이터는 유지
     * - 권한: ESG DELETE
     */
    @CheckPermission(resource = Resource.ESG, action = Action.DELETE)
    @DeleteMapping("/subjects/{subjectId}")
    public ResponseEntity<?> deleteSubject(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID subjectId) {
        esgService.deleteSubject(memberId, subjectId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "활동 양식 삭제 성공"),
                HttpStatus.OK
        );
    }

    // ═══════════════════════════════════════════════════════════
    // Activity
    // ═══════════════════════════════════════════════════════════

    /**
     * 활동 제출 (직원)
     * - verificationContent: 활동 내용 텍스트 (선택)
     * - file: 이미지(jpg/png), PDF, 인증서 등 첨부 파일 (선택)
     * - 텍스트와 파일 중 하나 이상 필수
     * - esgEnabledYn = YES 인 회사만 사용 가능
     */
    @PostMapping(value = "/activities", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> submitActivity(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestParam UUID subjectId,
            @RequestParam(required = false) String verificationContent,
            @RequestParam(required = false) MultipartFile file) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        esgService.submitActivity(memberId, subjectId, verificationContent, file),
                        "활동 제출 성공"),
                HttpStatus.CREATED
        );
    }

    /**
     * 전체 활동 목록 조회 (관리자)
     * - 회사 전체 직원의 활동 목록 조회
     * - status: PENDING / APPROVED / REJECTED 필터링 가능
     * - status 미입력 시 전체 조회
     * - 권한: ESG READ
     */
    @CheckPermission(resource = Resource.ESG, action = Action.READ)
    @GetMapping("/activities")
    public ResponseEntity<?> getActivityList(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestParam(required = false) ActivityStatus status) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        esgService.getActivityList(memberId, status), "활동 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 내 활동 목록 조회 (직원)
     * - 본인이 제출한 활동 목록만 조회
     * - status: PENDING / APPROVED / REJECTED 필터링 가능
     * - status 미입력 시 전체 조회
     */
    @GetMapping("/activities/my")
    public ResponseEntity<?> getMyActivityList(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestParam(required = false) ActivityStatus status) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        esgService.getMyActivityList(memberId, status), "내 활동 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 활동 승인 (관리자)
     * - status PENDING → APPROVED 변경
     * - 승인 시 해당 직원에게 포인트 자동 적립
     * - 이미 처리된 활동 재승인 불가
     * - 권한: ESG UPDATE
     */
    @CheckPermission(resource = Resource.ESG, action = Action.UPDATE)
    @PatchMapping("/activities/{activityId}/approve")
    public ResponseEntity<?> approveActivity(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID activityId) {
        esgService.approveActivity(memberId, activityId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "활동 승인 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 활동 반려 (관리자)
     * - status PENDING → REJECTED 변경
     * - 반려 사유 필수 입력
     * - 포인트 적립 없음
     * - 이미 처리된 활동 재반려 불가
     * - 권한: ESG UPDATE
     */
    @CheckPermission(resource = Resource.ESG, action = Action.UPDATE)
    @PatchMapping("/activities/{activityId}/reject")
    public ResponseEntity<?> rejectActivity(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID activityId,
            @Valid @RequestBody EsgRejectReqDto reqDto) {
        esgService.rejectActivity(memberId, activityId, reqDto);
        return new ResponseEntity<>(
                ApiResponse.success(null, "활동 반려 성공"),
                HttpStatus.OK
        );
    }

    // ═══════════════════════════════════════════════════════════
    // Point
    // ═══════════════════════════════════════════════════════════

    /**
     * 포인트 잔액 조회
     * - EsgPointHistory 의 가장 최근 balance 스냅샷 반환
     * - 이력이 없으면 0 반환
     * - 본인 잔액만 조회 가능
     */
    @GetMapping("/points/balance")
    public ResponseEntity<?> getCurrentBalance(
            @RequestHeader("X-User-UUID") UUID memberId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        esgService.getCurrentBalance(memberId), "포인트 잔액 조회 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 포인트 내역 조회
     * - 적립(EARN) / 사용(USE) 전체 이력 최신순 반환
     * - referenceType: ACTIVITY / SHOP_ORDER 구분 가능
     * - 본인 내역만 조회 가능
     */
    @GetMapping("/points/history")
    public ResponseEntity<?> getPointHistory(
            @RequestHeader("X-User-UUID") UUID memberId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        esgService.getPointHistory(memberId), "포인트 내역 조회 성공"),
                HttpStatus.OK
        );
    }

    // ═══════════════════════════════════════════════════════════
    // Score
    // ═══════════════════════════════════════════════════════════

    /**
     * 월별 ESG 점수 집계
     * - yearMonth: "2026-04" 형식
     * - 해당 월의 APPROVED 활동을 E / S / G 카테고리별로 집계
     * - totalScore = (eScore + sScore + gScore) / 3
     * - EsgGrade: BRONZE(0) / SILVER(40) / GOLD(70) / PLATINUM(90)
     * - 동일 월 재집계 시 기존 스냅샷 삭제 후 재생성
     */
    @PostMapping("/scores/{yearMonth}")
    public ResponseEntity<?> calculateScore(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable String yearMonth) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        esgService.calculateMyMonthlyScore(memberId, yearMonth), "점수 집계 성공"),
                HttpStatus.OK
        );
    }

    /**
     * ESG 점수 이력 조회
     * - 본인의 월별 ESG 점수 전체 이력 최신순 조회
     * - 등급 변화 추이 확인 가능
     */
    @GetMapping("/scores/history")
    public ResponseEntity<?> getScoreHistory(
            @RequestHeader("X-User-UUID") UUID memberId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        esgService.getMyScoreHistory(memberId), "점수 이력 조회 성공"),
                HttpStatus.OK
        );
    }

    // ═══════════════════════════════════════════════════════════
    // Shop
    // ═══════════════════════════════════════════════════════════

    /**
     * 물품 등록 (관리자)
     * - 물품명, 설명, 이미지, 필요 포인트, 재고 등록
     * - esgEnabledYn = YES 인 회사만 사용 가능
     * - 권한: ESG CREATE
     */
    @CheckPermission(resource = Resource.ESG, action = Action.CREATE)
    @PostMapping(value = "/shop/items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createShopItem(
            @RequestHeader("X-User-UUID") UUID memberId,
            @ModelAttribute EsgShopItemCreateReqDto reqDto) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        esgService.createShopItem(memberId, reqDto, reqDto.getImage()),
                        "물품 등록 성공"),
                HttpStatus.CREATED
        );
    }

    /**
     * 물품 목록 조회
     * - 재고 포함 전체 물품 조회
     * - 모든 직원 접근 가능
     */
    @GetMapping("/shop/items")
    public ResponseEntity<?> getShopItemList(
            @RequestHeader("X-User-UUID") UUID memberId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        esgService.getShopItemList(memberId), "물품 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 물품 수정 (관리자)
     * - 이미지 변경 시 기존 S3 파일 삭제 후 재업로드
     * - 권한: ESG UPDATE
     */
    @CheckPermission(resource = Resource.ESG, action = Action.UPDATE)
    @PutMapping(value = "/shop/items/{itemId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateShopItem(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID itemId,
            @ModelAttribute EsgShopItemCreateReqDto reqDto) {
        esgService.updateShopItem(memberId, itemId, reqDto, reqDto.getImage());
        return new ResponseEntity<>(
                ApiResponse.success(null, "물품 수정 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 물품 삭제 (관리자)
     * - 소프트 삭제 (delYn = YES)
     * - S3 이미지 삭제
     * - 권한: ESG DELETE
     */
    @CheckPermission(resource = Resource.ESG, action = Action.DELETE)
    @DeleteMapping("/shop/items/{itemId}")
    public ResponseEntity<?> deleteShopItem(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID itemId) {
        esgService.deleteShopItem(memberId, itemId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "물품 삭제 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 물품 구매 (직원)
     * - 보유 포인트로 물품 구매
     * - 재고 0이면 품절
     * - 잔액 부족 시 구매 불가
     * - 구매 성공 시 재고 차감 및 포인트 차감
     */
    @PostMapping("/shop/orders/{itemId}")
    public ResponseEntity<?> orderShopItem(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID itemId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        esgService.orderShopItem(memberId, itemId), "물품 구매 성공"),
                HttpStatus.CREATED
        );
    }

    /**
     * 내 구매 내역 조회 (직원)
     * - 본인의 물품 구매 이력 최신순 조회
     */
    @GetMapping("/shop/orders/my")
    public ResponseEntity<?> getMyShopOrders(
            @RequestHeader("X-User-UUID") UUID memberId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        esgService.getMyShopOrders(memberId), "내 구매 내역 조회 성공"),
                HttpStatus.OK
        );
    }

    /**
     * 전체 구매 내역 조회 (관리자)
     * - 회사 전체 직원의 구매 이력 조회
     * - 권한: ESG READ
     */
    @CheckPermission(resource = Resource.ESG, action = Action.READ)
    @GetMapping("/shop/orders")
    public ResponseEntity<?> getAllShopOrders(
            @RequestHeader("X-User-UUID") UUID memberId) {
        return new ResponseEntity<>(
                ApiResponse.success(
                        esgService.getAllShopOrders(memberId), "전체 구매 내역 조회 성공"),
                HttpStatus.OK
        );
    }
}