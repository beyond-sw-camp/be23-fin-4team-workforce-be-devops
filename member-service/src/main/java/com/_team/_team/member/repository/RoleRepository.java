package com._team._team.member.repository;

import com._team._team.member.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    // 회사별 역할 목록
    List<Role> findByCompany_CompanyIdAndDelYnOrderByDisplayOrder(
            UUID companyId, String delYn);
}
