package com._team._team.approval.controller;

import com._team._team.approval.dto.resdto.AttachmentResDto;
import com._team._team.approval.service.AttachmentService;
import com._team._team.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/approval/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    @Autowired
    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    // 첨부파일 업로드
    @PostMapping("/{requestId}")
    public ResponseEntity<?> upload(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID requestId,
            @RequestParam("files") List<MultipartFile> files) {

        List<AttachmentResDto> result = attachmentService.upload(memberId, requestId, files);
        return new ResponseEntity<>(
                ApiResponse.success(result, "첨부파일 업로드 성공"),
                HttpStatus.CREATED
        );
    }

    // 첨부파일 목록 조회
    @GetMapping("/{requestId}")
    public ResponseEntity<?> findByRequestId(
            @RequestHeader("X-User-CompanyId") UUID companyId,
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID requestId) {

        List<AttachmentResDto> result = attachmentService.findByRequestId(companyId, memberId, requestId);
        return new ResponseEntity<>(
                ApiResponse.success(result, "첨부파일 목록 조회 성공"),
                HttpStatus.OK
        );
    }

    // 첨부파일 삭제
    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<?> delete(
            @RequestHeader("X-User-UUID") UUID memberId,
            @PathVariable UUID attachmentId) {

        attachmentService.delete(memberId, attachmentId);
        return new ResponseEntity<>(
                ApiResponse.success(null, "첨부파일 삭제 성공"),
                HttpStatus.OK
        );
    }
}
