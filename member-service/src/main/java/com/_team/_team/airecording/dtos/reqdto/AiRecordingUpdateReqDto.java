package com._team._team.airecording.dtos.reqdto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiRecordingUpdateReqDto {
    @Size(max = 200, message = "제목은 200자 이내여야 합니다.")
    private String title;

    private String transcript;   // null이면 변경 안 함

    private String summary;      // null이면 변경 안 함
}
