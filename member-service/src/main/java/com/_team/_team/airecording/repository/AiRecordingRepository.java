package com._team._team.airecording.repository;

import com._team._team.airecording.domain.AiRecording;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiRecordingRepository extends JpaRepository<AiRecording, UUID> {
    /**
     * 내 녹음 목록 (삭제 안 된 것, 최신순, 페이징)
     * 화면 1 (목록 페이지)에서 사용
     */
    Page<AiRecording> findByMemberIdAndDelYnOrderByCreatedAtDesc(
            UUID memberId, String delYn, Pageable pageable);

    /**
     * 내 녹음 단건 조회 (삭제 안 된 것)
     * 화면 3 (상세 페이지) / 수정 / 삭제 시 사용
     * 본인 거 아니면 빈 Optional 반환 → Service에서 NOT_FOUND 처리
     */
    Optional<AiRecording> findByRecordingIdAndMemberIdAndDelYn(
            UUID recordingId, UUID memberId, String delYn);

    /**
     * 제목 검색 (옵션, 화면 1의 검색창 동작용)
     * 사용 안 하실 거면 지워도 됨
     */
    Page<AiRecording> findByMemberIdAndDelYnAndTitleContainingOrderByCreatedAtDesc(
            UUID memberId, String delYn, String titleKeyword, Pageable pageable);

    /**
     * 내 녹음 개수 (옵션, 통계/대시보드용)
     */
    long countByMemberIdAndDelYn(UUID memberId, String delYn);
}
