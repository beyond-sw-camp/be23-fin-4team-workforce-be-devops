package com._team._team.memberchat.controller;

import com._team._team.dto.ApiResponse;
import com._team._team.memberchat.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

// 채팅 첨부 업로드·다운로드
@RestController
@RequestMapping("/member-chat/files")
@RequiredArgsConstructor
public class ChatFileController {

    private final FileService fileService;

    // 업로드 주소 발급
    @PostMapping("/presigned")
    public ResponseEntity<?> presigned(@RequestHeader("X-User-UUID") UUID uploader,
                                       @RequestBody Map<String, Object> body) {
        String name = body.get("filename") == null ? "" : String.valueOf(body.get("filename"));
        Object rawMime = body.get("mime");
        String mime = null;
        if (rawMime != null) {
            String s = rawMime.toString().trim();
            if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) {
                mime = s;
            }
        }
        long size = ((Number) body.getOrDefault("size", 0)).longValue();
        return ResponseEntity.ok(ApiResponse.success(
                fileService.presignUpload(uploader, name, mime, size), "ok"));
    }

    // 서버가 S3에 직접 업로드 (브라우저 CORS 없음)
    // consumes 미지정: multipart boundary 포함 Content-Type 과 정확히 맞추지 않아도 매칭되도록 함
    @PostMapping("/upload")
    public ResponseEntity<?> uploadMultipart(@RequestHeader("X-User-UUID") UUID uploader,
                                             @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("빈 파일입니다."));
        }
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            name = "upload.bin";
        }
        try (InputStream in = file.getInputStream()) {
            return ResponseEntity.ok(ApiResponse.success(
                    fileService.uploadViaServer(uploader, name, file.getContentType(), in, file.getSize()), "ok"));
        } catch (IOException e) {
            throw new IllegalStateException("파일 읽기 실패", e);
        }
    }

    // 업로드 완료 확인
    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@RequestHeader("X-User-UUID") UUID uploader,
                                     @RequestBody Map<String, Object> body) {
        String key = String.valueOf(body.get("key"));
        Long messageId = body.get("messageId") == null ? null : ((Number) body.get("messageId")).longValue();
        return ResponseEntity.ok(ApiResponse.success(
                fileService.confirmUpload(uploader, key, messageId), "confirmed"));
    }

    /**
     * 키에 '/' 가 포함되므로 path variable 대신 query 로 받는다. (게이트웨이·서블릿의 %2F 제한 회피)
     */
    // 다운로드로 이동
    @GetMapping("/download")
    public ResponseEntity<?> download(@RequestParam("key") String key,
                                      @RequestParam(defaultValue = "CLEAN") String scanStatus) {
        String url = fileService.presignDownload(key, scanStatus);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    /**
     * 브라우저에서 <img src> 로 직접 로드하려면 Authorization 헤더를 붙일 수 없으므로,
     * JWT 로 인증된 XHR 로 먼저 presigned S3 URL 을 JSON 으로 받아 사용한다.
     * S3 presigned URL 은 서명이 포함되어 있어 인증 없이도 접근 가능하다.
     */
    @GetMapping("/signed-url")
    public ResponseEntity<?> signedUrl(@RequestParam("key") String key,
                                       @RequestParam(defaultValue = "CLEAN") String scanStatus) {
        String url = fileService.presignDownload(key, scanStatus);
        return ResponseEntity.ok(ApiResponse.success(Map.of("url", url, "key", key), "ok"));
    }
}
