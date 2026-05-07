package com._team._team.attendance.repository;

import com._team._team.attendance.domain.LeavePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 연차 정책 Repository
 * - 회사별 1건만 존재 (companyId + delYn으로 단건 조회)
 */
@Repository
public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, UUID> {

    /** 회사의 현행 연차 정책 조회 */
    List<LeavePolicy> findByCompanyIdAndDelYn(UUID companyId, String delYn);

    /** 회사 연차 정책 조회 */
    Optional<LeavePolicy> findByCompanyIdAndPolicyIdAndDelYn(UUID companyId, UUID policyId, String delYn);

    /** 촉진제 사용 중인 모든 회사 정책 */
    List<LeavePolicy> findByIsPromotionYnAndDelYn(String isPromotionYn, String delYn);
}
