package com._team._team.notification.repository;

import com._team._team.member.domain.Member;
import com._team._team.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // 알림 목록 조회 (최신순)
    List<Notification> findByMemberOrderByCreatedAtDesc(Member member);

    // 안읽은 알림 수
    int countByMemberAndIsRead(Member member, String isRead);

    // 전체 읽음 처리
    List<Notification> findByMemberAndIsRead(Member member, String isRead);

    List<Notification> findByMemberAndCreatedAtAfterOrderByCreatedAtAsc(
            Member member, LocalDateTime createdAt);

    // 알림 목록 조회 (삭제 제외)
    List<Notification> findByMemberAndDelYnOrderByCreatedAtDesc(Member member, String delYn);

    // 안읽은 알림 수 (삭제 제외)
    int countByMemberAndIsReadAndDelYn(Member member, String isRead, String delYn);

    // 전체 읽음 처리 (삭제 제외)
    List<Notification> findByMemberAndIsReadAndDelYn(Member member, String isRead, String delYn);
}