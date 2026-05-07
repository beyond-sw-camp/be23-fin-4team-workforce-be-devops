package com._team._team.member.repository;

import com._team._team.member.domain.MemberPosition;
import com._team._team.member.domain.Role;
import com._team._team.organization.domain.JobGrade;
import com._team._team.organization.domain.JobTitle;
import com._team._team.organization.domain.Organization;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemberPositionRepository extends JpaRepository<MemberPosition, UUID> {
    boolean existsByRoleAndDelYn(Role role, String delYn);
    List<MemberPosition> findByRole(Role role);

    // 조직별 MemberPosition 조회
    List<MemberPosition> findByOrganizationAndDelYn(Organization organization, String delYn);

    @Query("SELECT mp FROM MemberPosition mp " +
            "LEFT JOIN FETCH mp.role r " +
            "LEFT JOIN FETCH r.rolePermissionList rp " +
            "LEFT JOIN FETCH rp.permission " +
            "WHERE mp.memberPositionId = :memberPositionId")
    Optional<MemberPosition> findByIdWithRoleAndPermissions(
            @Param("memberPositionId") UUID memberPositionId);

    boolean existsByJobGradeAndDelYn(JobGrade jobGrade, String delYn);

    boolean existsByJobTitleAndDelYn(JobTitle jobTitle, String delYn);

    boolean existsByOrganizationAndDelYn(Organization organization, String delYn);

    @Query("SELECT mp FROM MemberPosition mp " +
            "JOIN FETCH mp.organization " +
            "JOIN FETCH mp.jobGrade " +
            "JOIN FETCH mp.jobTitle " +
            "JOIN FETCH mp.role " +
            "WHERE mp.memberPositionId = :memberPositionId")
    Optional<MemberPosition> findByIdWithDetails(
            @Param("memberPositionId") UUID memberPositionId);


    /// //
    // 결재 서비스용: 부서 + 직책으로 조회
    @Query("SELECT mp FROM MemberPosition mp " +
            "JOIN FETCH mp.member " +
            "JOIN FETCH mp.organization " +
            "JOIN FETCH mp.jobTitle " +
            "JOIN FETCH mp.jobGrade " +
            "WHERE mp.organization.organizationId = :organizationId " +
            "AND mp.jobTitle.jobTitleId = :jobTitleId " +
            "AND mp.isActiveYn = 'YES' AND mp.delYn = 'NO'")
    List<MemberPosition> findByOrganizationIdAndJobTitleId(
            @Param("organizationId") UUID organizationId,
            @Param("jobTitleId") UUID jobTitleId);

    // 결재 서비스용: 부서 + 직급으로 조회
    @Query("SELECT mp FROM MemberPosition mp " +
            "JOIN FETCH mp.member " +
            "JOIN FETCH mp.organization " +
            "JOIN FETCH mp.jobTitle " +
            "JOIN FETCH mp.jobGrade " +
            "WHERE mp.organization.organizationId = :organizationId " +
            "AND mp.jobGrade.jobGradeId = :jobGradeId " +
            "AND mp.isActiveYn = 'YES' AND mp.delYn = 'NO'")
    List<MemberPosition> findByOrganizationIdAndJobGradeId(
            @Param("organizationId") UUID organizationId,
            @Param("jobGradeId") UUID jobGradeId);

    // 결재 서비스용: memberPositionId로 상세 조회 (활성 상태만)
    @Query("SELECT mp FROM MemberPosition mp " +
            "JOIN FETCH mp.member " +
            "JOIN FETCH mp.organization " +
            "JOIN FETCH mp.jobTitle " +
            "JOIN FETCH mp.jobGrade " +
            "WHERE mp.memberPositionId = :memberPositionId " +
            "AND mp.isActiveYn = 'YES' AND mp.delYn = 'NO'")
    Optional<MemberPosition> findActiveByIdWithDetails(
            @Param("memberPositionId") UUID memberPositionId);


    List<MemberPosition> findByOrganization_OrganizationIdAndDelYn(UUID organizationId, String delYn);

    // 회사 직원 일괄 조회 시 활성 포지션 부서명 채우기 위한 용도
    @Query("SELECT mp FROM MemberPosition mp " +
            "JOIN FETCH mp.organization " +
            "WHERE mp.member.memberId IN :memberIds " +
            "AND mp.isActiveYn = 'YES' " +
            "AND mp.delYn = 'NO'")
    List<MemberPosition> findActivePositionsByMemberIds(
            @Param("memberIds") java.util.Collection<UUID> memberIds);
}
