package com._team._team.attendance.repository;

import com._team._team.attendance.domain.LeavePromotionLog;
import com._team._team.attendance.domain.enums.PromotionStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

import com._team._team.attendance.domain.LeavePromotionLog;
import com._team._team.attendance.domain.enums.PromotionLogStatus;
import com._team._team.attendance.domain.enums.PromotionStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeavePromotionLogRepository extends JpaRepository<LeavePromotionLog, UUID> {

    boolean existsByMemberBalanceIdAndStage(UUID memberBalanceId, PromotionStage stage);

    // 직원 본인 통보 이력 최신순 회신 화면용
    List<LeavePromotionLog> findByMemberIdOrderBySentOnDesc(UUID memberId);

    // 회사 단계별 상태별 통보 무응답자 리스트 조회용
    List<LeavePromotionLog> findByCompanyIdAndStageAndStatus(UUID companyId,
                                                             PromotionStage stage,
                                                             PromotionLogStatus status);

    // 워커가 2차 발송 시 회신 안 한 잔고만 거르기 위한 키 집합
    // 결과 잔고 ID 가 acknowledged 처리된 것이면 발송 대상에서 제외
    List<LeavePromotionLog> findByMemberBalanceIdAndStatus(UUID memberBalanceId,
                                                           PromotionLogStatus status);

    /** 회사 + 상태 조회 — 회신/강제지정 이력 화면용 */
    List<LeavePromotionLog> findByCompanyIdAndStatusOrderBySentOnDesc(UUID companyId,
                                                                       PromotionLogStatus status);
}