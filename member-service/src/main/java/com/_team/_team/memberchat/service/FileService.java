package com._team._team.memberchat.service;

import com._team._team.memberchat.MemberChatProperties;
import com._team._team.memberchat.domain.ChatMessageImage;
import com._team._team.memberchat.error.ChatErrorCode;
import com._team._team.memberchat.error.ChatException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.UUID;

/**
 * 파일 업로드 흐름:
 * 1) POST /files/presigned   : 서버가 MIME/확장자/용량 정책을 URL 서명에 고정하여 presigned PUT URL 반환
 * 2) 클라이언트가 S3 로 직접 PUT (브라우저 CORS 이슈 가능) 또는 POST /files/upload 로 서버 경유
 * 3) POST /files/confirm     : 서버가 S3 HEAD 로 실제 크기/Content-Type 검증 + metadata 저장 + AV 이벤트 발행
 * 4) GET  /files/download    : presigned GET URL 로 302
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final MemberChatProperties props;
    private final ApplicationEventPublisher events;

    public PresignedUploadResult presignUpload(UUID uploader, String originalFileName, String mimeType, long sizeBytes) {
        if (mimeType == null || mimeType.isBlank()
                || "application/octet-stream".equalsIgnoreCase(mimeType)
                || "null".equalsIgnoreCase(mimeType)) {
            mimeType = inferMimeFromFileName(originalFileName);
        }
        validatePolicy(originalFileName, mimeType, sizeBytes);

        String key = buildObjectKey(uploader, originalFileName);

        // 서명에 Content-Type/Length 를 포함해 클라이언트가 약속된 조건으로만 업로드 가능하도록 강제
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(props.getFiles().getBucket())
                .key(key)
                .contentType(mimeType)
                .contentLength(sizeBytes)
                .build();

        URL url = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofSeconds(props.getFiles().getPresignedTtlSeconds()))
                        .putObjectRequest(put).build())
                .url();

        return new PresignedUploadResult(url.toString(), key, mimeType, sizeBytes, originalFileName);
    }

    /**
     * 브라우저→S3 직접 PUT 대신 API 게이트웨이·동일 출처로만 통신 (CORS 회피).
     */
    public PresignedUploadResult uploadViaServer(UUID uploader, String originalFileName, String mimeType,
                                                 InputStream inputStream, long sizeBytes) {
        if (originalFileName == null || originalFileName.isBlank()) {
            originalFileName = "upload.bin";
        }
        if (mimeType == null || mimeType.isBlank()
                || "application/octet-stream".equalsIgnoreCase(mimeType)
                || "null".equalsIgnoreCase(mimeType)) {
            mimeType = inferMimeFromFileName(originalFileName);
        }
        validatePolicy(originalFileName, mimeType, sizeBytes);

        String key = buildObjectKey(uploader, originalFileName);
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(props.getFiles().getBucket())
                        .key(key)
                        .contentType(mimeType)
                        .contentLength(sizeBytes)
                        .build(),
                RequestBody.fromInputStream(inputStream , sizeBytes));

        confirmUpload(uploader, key, null);
        return new PresignedUploadResult("", key, mimeType, sizeBytes, originalFileName);
    }

    private String buildObjectKey(UUID uploader, String originalFileName) {
        return "mc/" + uploader + "/" + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().replace("-", "") + extensionOf(originalFileName);
    }

    /** 업로드 확인: HEAD 로 실제 저장된 파일의 크기/타입을 다시 검증. */
    public ChatMessageImage confirmUpload(UUID uploader, String key, Long messageId) {
        HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(props.getFiles().getBucket()).key(key).build());

        validatePolicy(key, head.contentType(), head.contentLength());

        ChatMessageImage entity = ChatMessageImage.builder()
                .url(key)
                .mimeType(head.contentType())
                .sizeBytes(head.contentLength())
                .scanStatus("PENDING_SCAN")
                .build();

        // AV 훅 이벤트 (별도 리스너가 ClamAV / S3 Malware Protection 호출)
        events.publishEvent(new FileUploadedEvent(uploader, key, head.contentType(), head.contentLength()));
        return entity;
    }

    /** 다운로드 프록시: 방 접근 권한 검사 후 1h presigned GET URL 생성. */
    public String presignDownload(String key, String scanStatus) {
        if (!"CLEAN".equals(scanStatus)) {
            throw new ChatException(ChatErrorCode.FILE_QUARANTINED);
        }
        URL url = s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(props.getFiles().getBucket()).key(key).build())
                .build()).url();
        return url.toString();
    }

    private String inferMimeFromFileName(String name) {
        String ext = extensionOf(name).toLowerCase();
        return switch (ext) {
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            case ".pdf" -> "application/pdf";
            default -> throw new ChatException(ChatErrorCode.VALIDATION_ERROR,
                    "파일 확장자로 MIME 을 판별할 수 없습니다: " + ext);
        };
    }

    private void validatePolicy(String name, String mime, Long size) {
        if (mime == null || !props.getFiles().getAllowedMimes().contains(mime)) {
            throw new ChatException(ChatErrorCode.VALIDATION_ERROR, "허용되지 않은 MIME: " + mime);
        }
        long maxBytes = props.getFiles().getMaxSizeMb() * 1024L * 1024L;
        if (size != null && size > maxBytes) {
            throw new ChatException(ChatErrorCode.VALIDATION_ERROR, "파일 크기 초과");
        }
        String ext = extensionOf(name).toLowerCase();
        if (!ext.matches("\\.(png|jpe?g|gif|webp|pdf)")) {
            throw new ChatException(ChatErrorCode.VALIDATION_ERROR, "허용되지 않은 확장자: " + ext);
        }
    }

    private String extensionOf(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.'));
    }

    public record PresignedUploadResult(String url, String key, String mimeType, long sizeBytes, String fileName) {}
    public record FileUploadedEvent(UUID uploader, String key, String mime, long size) {}
}
