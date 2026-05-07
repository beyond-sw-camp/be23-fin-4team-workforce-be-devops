package com._team._team.meeting.repository;

import com._team._team.meeting.domain.MeetingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MeetingRecordRepository extends JpaRepository<MeetingRecord, UUID> {

    List<MeetingRecord> findByMemberIdOrderByScheduledAtDesc(UUID memberId);

    List<MeetingRecord> findByManagerIdOrderByScheduledAtDesc(UUID managerId);

    List<MeetingRecord> findByMemberIdAndManagerIdOrderByScheduledAtDesc(UUID memberId, UUID managerId);

    List<MeetingRecord> findByParentRecord_MeetingRecordIdOrderByScheduledAtAsc(UUID parentRecordId);

    /** 특정 시즌에 대해 이미 생성된 피드백 면담이 있는지 확인 (중복 생성 방지) */
    boolean existsByRelatedSeasonIdAndCompanyId(UUID relatedSeasonId, UUID companyId);

    /** 시즌별 면담 목록 — 운영자 대시보드 / 멱등 재생성 */
    List<MeetingRecord> findByRelatedSeasonIdAndCompanyId(UUID relatedSeasonId, UUID companyId);
}
