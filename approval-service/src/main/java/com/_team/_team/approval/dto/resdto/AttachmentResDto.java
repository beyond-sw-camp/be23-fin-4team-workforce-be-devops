package com._team._team.approval.dto.resdto;

import com._team._team.approval.domain.Attachment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class AttachmentResDto {
    private UUID attachmentId;
    private UUID requestId;
    private String fileName;
    private String approvalUrl;
    private Long fileSize;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AttachmentResDto fromEntity(Attachment entity) {
        return AttachmentResDto.builder()
                .attachmentId(entity.getAttachmentId())
                .requestId(entity.getRequest().getRequestId())
                .fileName(entity.getFileName())
                .approvalUrl(entity.getApprovalUrl())
                .fileSize(entity.getFileSize())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
