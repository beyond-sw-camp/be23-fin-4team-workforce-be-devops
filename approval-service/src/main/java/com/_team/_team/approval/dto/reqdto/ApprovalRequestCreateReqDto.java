package com._team._team.approval.dto.reqdto;

import com._team._team.approval.domain.enums.RequestStatus;
import com._team._team.approval.domain.enums.ViewerType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ApprovalRequestCreateReqDto {

    @NotNull(message = "문서 ID는 필수입니다.")
    private UUID documentId;

    @NotNull(message = "내용은 필수입니다.")
    private String contentJson;

    @NotNull(message = "요청 상태는 필수입니다.")
    private RequestStatus requestStatus; // DRAFT or WAIT

    private List<ApprovalLineItem> approvalLines;

    private List<ViewerItem> viewers;

    private List<OfficialRecipientItem> recipients; // OFFICIAL 타입일 때만 사용 (최소 1개)

    private String isDeptVisibleYn; // 부서 문서함 공개 여부 ("Y" or "N", 기본 "Y")

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class ApprovalLineItem {

        @NotNull(message = "결재 순서는 필수입니다.")
        private Integer stepOrder;

        @NotNull(message = "결재자 직위 ID는 필수입니다.")
        private UUID approverMemberId;

        @NotNull
        private UUID approverMemberPositionId;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class ViewerItem {

        @NotNull(message = "참조/공람자 ID는 필수입니다.")
        private UUID viewerMemberId;

        @NotNull(message = "참조/공람자 직위 ID는 필수입니다.")
        private UUID viewerMemberPositionId;

        @NotNull(message = "참조/공람 구분은 필수입니다.")
        private ViewerType viewerType; // CC or CIRCULATION
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class OfficialRecipientItem {

        @NotNull(message = "수신 부서 ID는 필수입니다.")
        private UUID recipientOrganizationId;

        @NotNull(message = "수신 부서명은 필수입니다.")
        private String recipientOrganizationName;
    }

}
