package com._team._team.esg.dtos.resdto;
import com._team._team.esg.domain.EsgActivity;
import com._team._team.esg.domain.enums.ActivityStatus;
import com._team._team.esg.domain.enums.EsgCategory;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EsgActivityResDto {

    private UUID esgActivityId;
    private UUID memberId;
    private String memberName;
    private UUID subjectId;
    private String subjectTitle;
    private EsgCategory category;
    private String categoryDescription;
    private ActivityStatus status;
    private String verificationContent;
    private String fileUrl;
    private Integer earnedPoints;
    private String rejectReason;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;

    public static EsgActivityResDto fromEntity(EsgActivity activity) {
        return EsgActivityResDto.builder()
                .esgActivityId(activity.getEsgActivityId())
                .memberId(activity.getMember().getMemberId())
                .memberName(activity.getMember().getName())
                .subjectId(activity.getSubject().getEsgActivitySubjectId())
                .subjectTitle(activity.getSubject().getTitle())
                .category(activity.getCategory())
                .categoryDescription(activity.getCategory().getDescription())
                .status(activity.getStatus())
                .verificationContent(activity.getVerificationContent())
                .fileUrl(activity.getFileUrl())
                .earnedPoints(activity.getEarnedPoints())
                .rejectReason(activity.getRejectReason())
                .approvedAt(activity.getApprovedAt())
                .createdAt(activity.getCreatedAt())
                .build();
    }
}