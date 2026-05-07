package com._team._team.airecording.domain;

import com._team._team.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Builder
@Entity
public class AiRecording extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false)
    private UUID recordingId;

    // === 소유자 ===
    @Column(nullable = false)
    private UUID companyId;          // 멀티테넌시. 사용자가 속한 회사

    @Column(nullable = false)
    private UUID memberId;            // 작성자 (본인 녹음만 보이도록 필터)

    // === 사용자 입력 ===
    @Column(nullable = false, length = 200)
    private String title;             // 사용자 입력 또는 자동 생성된 제목

    // === 음성 파일 정보 (S3) ===
    @Column(nullable = false, length = 500)
    private String audioUrl;          // S3 URL (https://...amazonaws.com/...)

    @Column(nullable = false, length = 200)
    private String audioFileName;     // 원본 파일명 (예: meeting.webm)

    @Column(nullable = false)
    private Long audioSize;           // 바이트

    // === 변환 결과 ===
    @Column(nullable = false, length = 10)
    private String language;          // "ko", "en", "ja" 등

    @Column(columnDefinition = "TEXT")
    private String transcript;        // Whisper 받아쓰기 원문

    @Column(columnDefinition = "TEXT")
    private String summary;           // GPT 회의록 정리 (마크다운)

    // === Soft delete ===
    @Builder.Default
    @Column(nullable = false, length = 1)
    private String delYn = "N";


    // ============================================================
    // 비즈니스 메서드 (Setter 대신 의미 있는 이름으로)
    // ============================================================
    public void updateTitle(String newTitle) {
        this.title = newTitle;
    }

    public void updateTranscript(String newTranscript) {
        this.transcript = newTranscript;
    }

    public void updateSummary(String newSummary) {
        this.summary = newSummary;
    }

    public void softDelete() {
        this.delYn = "Y";
    }
}
