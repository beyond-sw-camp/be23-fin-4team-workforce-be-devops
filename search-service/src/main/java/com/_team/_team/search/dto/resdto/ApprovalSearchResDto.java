package com._team._team.search.dto.resdto;

import com._team._team.search.domain.ApprovalDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalSearchResDto {

    private String requestId;
    private String memberId;
    private String requesterName;
    private String requesterOrganizationName;
    private String documentName;
    private String requestStatus;
    private String requestType;
    private LocalDateTime createdAt;
    /** 프론트에서 핵심 날짜(휴가일, 연장일 등) 추출용 */
    private String contentJson;

    public static ApprovalSearchResDto fromDocument(ApprovalDocument doc) {
        return ApprovalSearchResDto.builder()
                .requestId(doc.getRequestId())
                .memberId(doc.getMemberId())
                .requesterName(doc.getRequesterName())
                .requesterOrganizationName(doc.getRequesterOrganizationName())
                .documentName(doc.getDocumentName())
                .requestStatus(doc.getRequestStatus())
                .requestType(doc.getRequestType())
                .createdAt(doc.getCreatedAt())
                .contentJson(doc.getContentJson())
                .build();
    }
}