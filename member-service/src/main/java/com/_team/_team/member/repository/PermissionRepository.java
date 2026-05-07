package com._team._team.member.repository;

import com._team._team.annotation.Action;
import com._team._team.annotation.Resource;
import com._team._team.member.domain.Permission;
import com._team._team.member.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    // resource + action으로 권한 조회
    Optional<Permission> findByResourceAndAction(Resource resource, Action action);
}
