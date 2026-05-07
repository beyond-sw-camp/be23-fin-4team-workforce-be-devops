package com._team._team.approval.dto.reqdto;

import com._team._team.approval.domain.ApprovalDocument;
import com._team._team.approval.domain.enums.RequestType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ApprovalDocumentCreateReqDto {

    @NotBlank(message = "문서 이름은 필수입니다.")
    private String documentName;

    @NotNull(message = "입력 필드 정의(formSchema)는 필수입니다.")
    private String formSchema;

    @NotNull(message = "요청 타입은 필수입니다.")
    private RequestType requestType;

    private String isCalendarVisibleYn;
    private String calendarDisplayName;
    private String calendarStartField;
    private String calendarEndField;
    private String calendarTitleField;

    public ApprovalDocument toEntity(UUID companyId) {
        return ApprovalDocument.builder()
                .companyId(companyId)
                .documentName(documentName)
                .formSchema(formSchema)
                .requestType(requestType)
                .isCalendarVisibleYn(isCalendarVisibleYn != null ? isCalendarVisibleYn : "N")
                .calendarDisplayName(calendarDisplayName)
                .calendarStartField(calendarStartField)
                .calendarEndField(calendarEndField)
                .calendarTitleField(calendarTitleField)
                .build();
    }
}
