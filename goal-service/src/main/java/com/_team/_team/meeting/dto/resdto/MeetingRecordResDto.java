package com._team._team.meeting.dto.resdto;

import com._team._team.meeting.domain.MeetingRecord;
import com._team._team.meeting.domain.enums.Reaction;
import com._team._team.meeting.domain.enums.RepeatCycle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingRecordResDto {

    private UUID meetingRecordId;
    private UUID parentRecordId;
    private UUID memberId;
    private UUID managerId;
    private RepeatCycle repeatCycle;
    private LocalDateTime scheduledAt;
    private LocalDateTime completedAt;
    private String agenda;
    private String memo;
    private String privateMemo;
    private Reaction managerReaction;
    private Reaction memberReaction;
    private String relatedGoalIdsJson;
    private UUID relatedEvaluationResponseId;
    private UUID relatedSeasonId;
    private UUID companyId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MeetingRecordResDto from(MeetingRecord meetingRecord) {
        return MeetingRecordResDto.builder()
                .meetingRecordId(meetingRecord.getMeetingRecordId())
                .parentRecordId(meetingRecord.getParentRecord() != null
                        ? meetingRecord.getParentRecord().getMeetingRecordId()
                        : null)
                .memberId(meetingRecord.getMemberId())
                .managerId(meetingRecord.getManagerId())
                .repeatCycle(meetingRecord.getRepeatCycle())
                .scheduledAt(meetingRecord.getScheduledAt())
                .completedAt(meetingRecord.getCompletedAt())
                .agenda(meetingRecord.getAgenda())
                .memo(meetingRecord.getMemo())
                .privateMemo(meetingRecord.getPrivateMemo())
                .managerReaction(meetingRecord.getManagerReaction())
                .memberReaction(meetingRecord.getMemberReaction())
                .relatedGoalIdsJson(meetingRecord.getRelatedGoalIdsJson())
                .relatedEvaluationResponseId(meetingRecord.getRelatedEvaluationResponseId())
                .relatedSeasonId(meetingRecord.getRelatedSeasonId())
                .companyId(meetingRecord.getCompanyId())
                .createdAt(meetingRecord.getCreatedAt())
                .updatedAt(meetingRecord.getUpdatedAt())
                .build();
    }
}
