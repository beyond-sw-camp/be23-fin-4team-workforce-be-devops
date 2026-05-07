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
public class AiRecordingResDto {
    private UUID recordingId;
    private String title;

    // 음성 파일
    private String audioUrl;
    private String audioFileName;
    private Long audioSize;

    // 변환 결과
    private String language;
    private String transcript;
    private String summary;

    // 시간 정보
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;


    public static AiRecordingResDto fromEntity(AiRecording entity) {
        return AiRecordingResDto.builder()
                .recordingId(entity.getRecordingId())
                .title(entity.getTitle())
                .audioUrl(entity.getAudioUrl())
                .audioFileName(entity.getAudioFileName())
                .audioSize(entity.getAudioSize())
                .language(entity.getLanguage())
                .transcript(entity.getTranscript())
                .summary(entity.getSummary())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
