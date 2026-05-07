package com._team._team.airecording.feignclient.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TranscribeResDto {
    private String transcript;   // Whisper 받아쓰기 원문
    private String summary;      // GPT가 정리한 회의록 마크다운
    private String language;     // 사용된 언어 코드 ("ko" 등)
}
