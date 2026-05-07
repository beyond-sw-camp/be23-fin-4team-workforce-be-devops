package com._team._team.airecording.controller;

import com._team._team.airecording.dtos.reqdto.AiRecordingUpdateReqDto;
import com._team._team.airecording.dtos.resdto.AiRecordingListResDto;
import com._team._team.airecording.dtos.resdto.AiRecordingResDto;
import com._team._team.airecording.service.AiRecordingService;
import com._team._team.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/member/ai-recordings")
@RequiredArgsConstructor
public class AiRecordingController {

    private final AiRecordingService aiRecordingService;

    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "language", required = false, defaultValue = "ko") String language) {

        AiRecordingResDto result = aiRecordingService.create(
                companyId, memberId, audio, title, language);

        return new ResponseEntity<>(
                ApiResponse.success(result, "녹음이 저장되었습니다."),
                HttpStatus.CREATED);
    }


    /**
     * 내 녹음 목록 (페이징)
     * - keyword: 제목 검색어 (선택)
     * - 페이징 파라미터: ?page=0&size=20  (기본: 0페이지, 20개, 최신순)
     */
    @GetMapping
    public ResponseEntity<?> findMyRecordings(
            @RequestHeader("X-User-UUID") UUID memberId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<AiRecordingListResDto> result = aiRecordingService.findMyRecordings(
                memberId, keyword, pageable);

        return new ResponseEntity<>(
                ApiResponse.success(result, "녹음 목록 조회 성공"),
                HttpStatus.OK);
    }


    /**
     * 단건 상세 (본인 거만)
     */
    @GetMapping("/{recordingId}")
    public ResponseEntity<?> findById(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID recordingId) {

        AiRecordingResDto result = aiRecordingService.findById(memberId, recordingId);

        return new ResponseEntity<>(
                ApiResponse.success(result, "녹음 조회 성공"),
                HttpStatus.OK);
    }


    /**
     * 부분 수정 (title / transcript / summary 중 들어온 것만)
     */
    @PatchMapping("/{recordingId}")
    public ResponseEntity<?> update(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID recordingId,
            @Valid @RequestBody AiRecordingUpdateReqDto reqDto) {

        AiRecordingResDto result = aiRecordingService.update(
                memberId, recordingId, reqDto);

        return new ResponseEntity<>(
                ApiResponse.success(result, "녹음이 수정되었습니다."),
                HttpStatus.OK);
    }


    /**
     * 삭제 (soft delete + S3 객체 정리)
     */
    @DeleteMapping("/{recordingId}")
    public ResponseEntity<?> delete(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID recordingId) {

        aiRecordingService.delete(memberId, recordingId);

        return new ResponseEntity<>(
                ApiResponse.success(null, "녹음이 삭제되었습니다."),
                HttpStatus.OK);
    }

}
