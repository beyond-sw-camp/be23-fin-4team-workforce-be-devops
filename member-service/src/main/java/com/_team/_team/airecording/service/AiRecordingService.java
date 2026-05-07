package com._team._team.airecording.service;

import com._team._team.airecording.domain.AiRecording;
import com._team._team.airecording.dtos.reqdto.AiRecordingUpdateReqDto;
import com._team._team.airecording.dtos.resdto.AiRecordingListResDto;
import com._team._team.airecording.dtos.resdto.AiRecordingResDto;
import com._team._team.airecording.feignclient.AiServiceClient;
import com._team._team.airecording.feignclient.dtos.TranscribeResDto;
import com._team._team.airecording.repository.AiRecordingRepository;
import com._team._team.dto.BusinessException;
import com._team._team.s3.S3Uploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class AiRecordingService {

    private final AiRecordingRepository aiRecordingRepository;
    private final AiServiceClient aiServiceClient;
    private final S3Uploader s3Uploader;

    // Whisper API 한도 (25MB)
    private static final long MAX_AUDIO_BYTES = 25L * 1024 * 1024;

    // 허용 확장자 (S3Uploader 정규식과 일치시킬 것)
    private static final Set<String> ALLOWED_AUDIO_EXTS =
            Set.of("webm", "m4a", "mp3", "wav", "ogg");

    private static final String S3_DIRECTORY = "ai-recordings";

    private static final DateTimeFormatter TITLE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

//    1. 생성: 음성 -> AI 변환 -> s3 -> DB
//       순서: AI 먼저 (실패 시 S3 비용 절약)
    public AiRecordingResDto create(UUID companyId, UUID memberId,
                                    MultipartFile audio,
                                    String title,
                                    String language){

        // (1) 검증
        validateAudio(audio);
        String resolvedLanguage = resolveLanguage(language);
        String resolvedTitle = resolveTitle(title);

        // (2) ai-service 호출 (Feign)
        TranscribeResDto transcribeResult;
        try {
            transcribeResult = aiServiceClient.transcribe(
                    audio, resolvedLanguage, companyId, memberId);
        } catch (Exception e) {
            log.error("[create] ai-service 호출 실패: {}", e.getMessage(), e);
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "AI 변환에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }

        // (3) S3 업로드
        String audioUrl;
        try {
            audioUrl = s3Uploader.upload(audio, S3_DIRECTORY);
        } catch (Exception e) {
            log.error("[create] S3 업로드 실패: {}", e.getMessage(), e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "음성 파일 저장에 실패했습니다.");
        }

        // (4) DB 저장 (실패 시 S3 객체 정리)
        try {
            AiRecording entity = AiRecording.builder()
                    .companyId(companyId)
                    .memberId(memberId)
                    .title(resolvedTitle)
                    .audioUrl(audioUrl)
                    .audioFileName(audio.getOriginalFilename())
                    .audioSize(audio.getSize())
                    .language(resolvedLanguage)
                    .transcript(transcribeResult.getTranscript())
                    .summary(transcribeResult.getSummary())
                    .build();

            AiRecording saved = aiRecordingRepository.save(entity);
            return AiRecordingResDto.fromEntity(saved);

        } catch (Exception e) {
            log.error("[create] DB 저장 실패, S3 롤백 시도: {}", e.getMessage(), e);
            try {
                s3Uploader.delete(audioUrl);
            } catch (Exception cleanupErr) {
                log.warn("[create] S3 롤백 실패 (수동 정리 필요): {}", audioUrl);
            }
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "녹음 저장에 실패했습니다.");
        }
    }

    //    2. 내 녹음 목록(최신순, 페이징, 제목 검색 옵션)
    @Transactional(readOnly = true)
    public Page<AiRecordingListResDto> findMyRecordings(
            UUID memberId, String keyword, Pageable pageable) {

        Page<AiRecording> page;
        if (keyword == null || keyword.isBlank()) {
            page = aiRecordingRepository
                    .findByMemberIdAndDelYnOrderByCreatedAtDesc(
                            memberId, "N", pageable);
        } else {
            page = aiRecordingRepository
                    .findByMemberIdAndDelYnAndTitleContainingOrderByCreatedAtDesc(
                            memberId, "N", keyword.trim(), pageable);
        }
        return page.map(AiRecordingListResDto::fromEntity);
    }

    //    3. 단건 상세
    @Transactional(readOnly = true)
    public AiRecordingResDto findById(UUID memberId, UUID recordingId) {
        AiRecording entity = findOwnedRecording(memberId, recordingId);
        return AiRecordingResDto.fromEntity(entity);
    }

    // 4. 부분 수정
    public AiRecordingResDto update(UUID memberId, UUID recordingId,
                                    AiRecordingUpdateReqDto reqDto) {
        AiRecording entity = findOwnedRecording(memberId, recordingId);

        if (reqDto.getTitle() != null && !reqDto.getTitle().isBlank()) {
            entity.updateTitle(reqDto.getTitle().trim());
        }
        if (reqDto.getTranscript() != null) {
            entity.updateTranscript(reqDto.getTranscript());
        }
        if (reqDto.getSummary() != null) {
            entity.updateSummary(reqDto.getSummary());
        }
        // @Transactional + dirty checking → flush 자동
        return AiRecordingResDto.fromEntity(entity);
    }


    //    5. 소프트딜리트, s3 객체 삭제
    public void delete(UUID memberId, UUID recordingId) {
        AiRecording entity = findOwnedRecording(memberId, recordingId);
        entity.softDelete();

        // S3 삭제 실패해도 soft delete는 유지 (UI 노출은 안 됨)
        try {
            s3Uploader.delete(entity.getAudioUrl());
        } catch (Exception e) {
            log.warn("[delete] S3 객체 삭제 실패 (DB는 soft delete 완료): {} / {}",
                    entity.getAudioUrl(), e.getMessage());
        }
    }

    private void validateAudio(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "음성 파일이 비어있습니다.");
        }
        if (audio.getSize() > MAX_AUDIO_BYTES) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "음성 파일은 25MB 이하여야 합니다. 더 짧게 녹음해주세요.");
        }
        String ext = extractExt(audio.getOriginalFilename());
        if (ext == null || !ALLOWED_AUDIO_EXTS.contains(ext)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "지원하지 않는 음성 형식입니다. (webm/m4a/mp3/wav/ogg)");
        }
    }

    private String extractExt(String filename) {
        if (filename == null) return null;
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return null;
        return filename.substring(dot + 1).toLowerCase();
    }

    private String resolveLanguage(String language) {
        if (language == null || language.isBlank()) return "ko";
        return language.trim().toLowerCase();
    }

    private String resolveTitle(String title) {
        if (title != null && !title.isBlank()) return title.trim();
        // 자동 생성: "녹음 2026-04-28 14:30"
        return "녹음 " + LocalDateTime.now().format(TITLE_FORMATTER);
    }

    private AiRecording findOwnedRecording(UUID memberId, UUID recordingId) {
        return aiRecordingRepository
                .findByRecordingIdAndMemberIdAndDelYn(recordingId, memberId, "N")
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "녹음을 찾을 수 없습니다."));
    }

}
