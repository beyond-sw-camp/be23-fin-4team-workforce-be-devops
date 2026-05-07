package com._team._team.approval.dto.reqdto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ApprovalDocumentUpdateReqDto {

    @NotNull(message = "입력 필드 정의(formSchema)는 필수입니다.")
    private String formSchema;

    private String isCalendarVisibleYn;
    private String calendarDisplayName;
    private String calendarStartField;
    private String calendarEndField;
    private String calendarTitleField;
}
