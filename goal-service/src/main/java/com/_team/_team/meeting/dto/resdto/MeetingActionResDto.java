package com._team._team.meeting.dto.resdto;

import com._team._team.meeting.domain.MeetingAction;
import com._team._team.meeting.domain.enums.TlRating;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingActionResDto {

    private UUID meetingActionId;
    private UUID meetingRecordId;
    private UUID assigneeId;
    private String description;
    private LocalDate dueDate;
    private boolean isCompleted;
    private LocalDateTime completedAt;
    private TlRating tlRating;
    private UUID approvalId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MeetingActionResDto from(MeetingAction meetingAction) {
        return MeetingActionResDto.builder()
                .meetingActionId(meetingAction.getMeetingActionId())
                .meetingRecordId(meetingAction.getMeetingRecord().getMeetingRecordId())
                .assigneeId(meetingAction.getAssigneeId())
                .description(meetingAction.getDescription())
                .dueDate(meetingAction.getDueDate())
                .isCompleted(meetingAction.isCompleted())
                .completedAt(meetingAction.getCompletedAt())
                .tlRating(meetingAction.getTlRating())
                .approvalId(meetingAction.getApprovalId())
                .createdAt(meetingAction.getCreatedAt())
                .updatedAt(meetingAction.getUpdatedAt())
                .build();
    }
}
