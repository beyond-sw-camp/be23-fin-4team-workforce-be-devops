package com._team._team.meeting.repository;

import com._team._team.meeting.domain.MeetingAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MeetingActionRepository extends JpaRepository<MeetingAction, UUID> {

    List<MeetingAction> findByMeetingRecord_MeetingRecordIdOrderByCreatedAtAsc(UUID meetingRecordId);

    List<MeetingAction> findByAssigneeIdAndIsCompletedFalse(UUID assigneeId);
}
