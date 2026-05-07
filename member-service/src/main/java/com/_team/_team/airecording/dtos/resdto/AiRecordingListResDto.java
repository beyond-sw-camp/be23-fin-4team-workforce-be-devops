package com._team._team.airecording.dtos.resdto;

import com._team._team.airecording.domain.AiRecording;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiRecordingListResDto {
    private UUID recordingId;
    private String title;
    private String audioFileName;
    private Long audioSize;
    private String language;

    /** 목록 카드에 미리보기로 표시할 짧은 요약 (summary 앞부분) */
    private String summaryPreview;

    private LocalDateTime createdAt;


    private static final int PREVIEW_MAX_LENGTH = 120;

    public static AiRecordingListResDto fromEntity(AiRecording entity) {
        return AiRecordingListResDto.builder()
                .recordingId(entity.getRecordingId())
                .title(entity.getTitle())
                .audioFileName(entity.getAudioFileName())
                .audioSize(entity.getAudioSize())
                .language(entity.getLanguage())
                .summaryPreview(buildPreview(entity.getSummary()))
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /**
     * summary 마크다운에서 헤더(##)/리스트 마크(-)/공백 정리하고 앞 120자만.
     * 목록 카드의 미리보기 줄에 적합한 형태로 변환.
     */
    private static String buildPreview(String summary) {
        if (summary == null || summary.isBlank()) return "";

        String stripped = summary
                .replaceAll("(?m)^#+\\s*", "")    // ## 제거
                .replaceAll("(?m)^[-*]\\s*", "")  // - 또는 * 제거
                .replaceAll("\\*\\*", "")          // **bold** 제거
                .replaceAll("\\s+", " ")           // 다중 공백 -> 하나
                .trim();

        if (stripped.length() <= PREVIEW_MAX_LENGTH) {
            return stripped;
        }
        return stripped.substring(0, PREVIEW_MAX_LENGTH) + "...";
    }
}
