package com._team._team.evaluation.service;

import com._team._team.annotation.Action;
import com._team._team.annotation.Resource;
import com._team._team.dto.BusinessException;
import com._team._team.goal.feignclients.MemberServiceClient;
import com._team._team.goal.feignclients.dto.MemberOrgContextDto;
import com._team._team.goal.permission.PositionPermissionReader;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class EvaluationAccessScopeService {

    private final PositionPermissionReader permissionReader;
    private final MemberServiceClient memberServiceClient;

    public EvaluationAccessScopeService(PositionPermissionReader permissionReader,
                                        MemberServiceClient memberServiceClient) {
        this.permissionReader = permissionReader;
        this.memberServiceClient = memberServiceClient;
    }

    public AccessScope resolveOperationReadScope(HttpServletRequest request, UUID companyId, UUID requesterId) {
        if (requesterId == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "요청자 정보가 없습니다.");
        }
        if (canReadCompany(request)) {
            return AccessScope.companyWide(requesterId);
        }
        if (!canReadTeam(request)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "평가 운영 정보를 조회할 권한이 없습니다.");
        }

        Set<UUID> visibleMemberIds = new LinkedHashSet<>();
        visibleMemberIds.add(requesterId);
        UUID organizationId = findOrganizationId(companyId, requesterId);
        if (organizationId != null) {
            visibleMemberIds.addAll(memberServiceClient.findMemberIdsByOrgId(companyId, organizationId));
        }
        return AccessScope.teamScoped(requesterId, visibleMemberIds);
    }

    private boolean canReadCompany(HttpServletRequest request) {
        if (permissionReader.isSystemAdmin(request)) {
            return true;
        }
        return permissionReader.hasPermissionInAnyRange(request, Resource.EVALUATION, Action.READ, Set.of("COMPANY"))
                || permissionReader.hasPermissionInAnyRange(request, Resource.EVALUATION, Action.UPDATE, Set.of("COMPANY"));
    }

    private boolean canReadTeam(HttpServletRequest request) {
        return permissionReader.hasPermissionInAnyRange(request, Resource.EVALUATION, Action.READ, Set.of("TEAM"))
                || permissionReader.hasPermissionInAnyRange(request, Resource.EVALUATION, Action.UPDATE, Set.of("TEAM"));
    }

    private UUID findOrganizationId(UUID companyId, UUID requesterId) {
        try {
            MemberOrgContextDto context = memberServiceClient.getOrgContext(requesterId);
            return context != null ? context.getOrganizationId() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public record AccessScope(boolean companyWide, UUID requesterId, Set<UUID> visibleMemberIds) {
        public static AccessScope companyWide(UUID requesterId) {
            return new AccessScope(true, requesterId, Set.of());
        }

        public static AccessScope teamScoped(UUID requesterId, Set<UUID> visibleMemberIds) {
            return new AccessScope(false, requesterId, visibleMemberIds != null ? Set.copyOf(visibleMemberIds) : Set.of(requesterId));
        }

        public boolean canSeeTarget(UUID memberId) {
            return companyWide || memberId != null && visibleMemberIds.contains(memberId);
        }

        public boolean isRequester(UUID memberId) {
            return requesterId != null && requesterId.equals(memberId);
        }
    }
}
