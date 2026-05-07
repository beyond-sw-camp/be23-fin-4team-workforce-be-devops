package com._team._team.s3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3Uploader {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.region}")
    private String region;

    public String upload(MultipartFile file, String dirName) {
        // 파일 유효성 검사
        validateFile(file);

        String fileName = buildFileName(dirName, file.getOriginalFilename());

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
            log.info("S3 업로드 성공: {}", fileName);

        } catch (IOException e) {
            log.error("S3 업로드 실패: {}", fileName, e);
            throw new RuntimeException("S3 파일 업로드 실패: " + file.getOriginalFilename(), e);
        }

        return buildFileUrl(fileName);
    }

    /**
     * 다중 파일 업로드
     * @param files   업로드할 파일 목록
     * @param dirName S3 저장 경로
     * @return 업로드된 파일 URL 목록
     */
    public List<String> uploadList(List<MultipartFile> files, String dirName) {
        return files.stream()
                .map(file -> upload(file, dirName))
                .toList();
    }

    /**
     * 단일 파일 삭제
     * @param fileUrl 삭제할 파일의 S3 URL
     */
    public void delete(String fileUrl) {
        String fileName = extractFileName(fileUrl);

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(fileName)
                .build();

        s3Client.deleteObject(request);
        log.info("S3 삭제 성공: {}", fileName);
    }

    /**
     * 다중 파일 삭제
     * @param fileUrls 삭제할 파일 URL 목록
     */
    public void deleteList(List<String> fileUrls) {
        fileUrls.parallelStream().forEach(this::delete);
    }

    // 파일 유효성 검사
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다.");
        }
        // 허용 확장자 체크
        String originalName = file.getOriginalFilename();
//        if (originalName == null || !originalName.matches(".*\\.(jpg|jpeg|png|gif|pdf|docx)$")) {
//        if (originalName == null || !originalName.matches(".*\\.(jpg|jpeg|png|gif|pdf|docx|doc|xlsx|xls|pptx|ppt|hwp|hwpx|txt|csv|zip)$")) {
        if (originalName == null || !originalName.matches(".*\\.(jpg|jpeg|png|gif|pdf|docx|doc|xlsx|xls|pptx|ppt|hwp|hwpx|txt|csv|zip|webm|m4a|mp3|wav|ogg)$")) {
            throw new IllegalArgumentException("허용되지 않는 파일 형식입니다: " + originalName);
        }
    }

    // 파일명 생성 (중복 방지 UUID 포함)
    private String buildFileName(String dirName, String originalName) {
        return dirName + "/" + UUID.randomUUID() + "_" + originalName;
    }

    // S3 URL 생성
    private String buildFileUrl(String fileName) {
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + fileName;
    }

    // URL에서 파일명 추출
    private String extractFileName(String fileUrl) {
        return fileUrl.split("amazonaws.com/")[1];
    }
}