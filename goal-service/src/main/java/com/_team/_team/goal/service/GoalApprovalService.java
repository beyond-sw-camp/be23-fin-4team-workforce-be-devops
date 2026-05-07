package com._team._team.goal.service;

import com._team._team.dto.BusinessException;
import com._team._team.dto.NotificationMessage;
import com._team._team.goal.domain.Goal;
import com._team._team.goal.domain.GoalApprovalBundle;
import com._team._team.goal.domain.enums.BundleApprovalStatus;
import com._team._team.goal.domain.enums.GoalOwnerType;
import com._team._team.goal.domain.enums.GoalStatus;
import com._team._team.goal.domain.enums.KpiCycle;
import com._team._team.goal.dto.reqdto.BundleApproveReqDto;
import com._team._team.goal.dto.reqdto.BundleRejectReqDto;
import com._team._team.goal.dto.reqdto.SubmitCycleReqDto;
import com._team._team.goal.dto.resdto.BundleResDto;
import com._team._team.goal.feignclients.MemberServiceClient;
import com._team._team.goal.repository.GoalApprovalBundleRepository;
import com._team._team.goal.repository.GoalRepository;
import com._team._team.goal.util.CycleKeyResolver;
import com._team._team.notification.NotificationType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GoalApprovalService {

    private final GoalRepository goalRepository;
    private final GoalApprovalBundleRepository bundleRepository;
    private final MemberServiceClient memberServiceClient;
    private final ApplicationEventPublisher eventPublisher;

    public GoalApprovalService(GoalRepository goalRepository,
                               GoalApprovalBundleRepository bundleRepository,
                               MemberServiceClient memberServiceClient,
                               ApplicationEventPublisher eventPublisher) {
        this.goalRepository = goalRepository;
        this.bundleRepository = bundleRepository;
        this.memberServiceClient = memberServiceClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public BundleResDto submitCycle(UUID requesterId,
                                    UUID companyId,
                                    String cycleKey,
                                    SubmitCycleReqDto dto) {
        Optional<GoalApprovalBundle> existingPending = bundleRepository
                .findFirstByRequestedByAndCycleKeyAndStatus(requesterId, cycleKey, BundleApprovalStatus.PENDING);
        if (existingPending.isPresent()) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "There is already a pending approval bundle for this cycle. bundleId=" + existingPending.get().getBundleId()
            );
        }
        if (bundleRepository.existsByRequestedByAndCycleKeyAndStatus(
                requesterId,
                cycleKey,
                BundleApprovalStatus.APPROVED
        )) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "This cycle has already been approved. Approved goals cannot be resubmitted. cycleKey=" + cycleKey
            );
        }

        CycleKeyParsed parsed = parseCycleKey(cycleKey);
        Set<GoalStatus> draftSet = EnumSet.of(GoalStatus.DRAFT);
        List<Goal> draftGoals = goalRepository
                .findByOwnerIdAndCycleAndStatusIn(requesterId, parsed.cycle, draftSet)
                .stream()
                .filter(goal -> goal.getOwnerType() == GoalOwnerType.MEMBER)
                .filter(goal -> cycleKey.equalsIgnoreCase(CycleKeyResolver.resolve(goal.getCycle(), goal.getCycleStartDate())))
                .toList();

        if (draftGoals.isEmpty()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "There are no draft KRs for this cycle. cycleKey=" + cycleKey
            );
        }

        int sum = draftGoals.stream().mapToInt(Goal::getWeightPct).sum();
        if (sum != 100) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "KR weight sum must be 100 for submission. current=" + sum
            );
        }

        for (Goal goal : draftGoals) {
            if (goal.getAlignedOrgGoalId() == null) {
                throw new BusinessException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "All KRs must be aligned to an objective before submission. goalId=" + goal.getGoalId()
                );
            }
            Goal objective = goalRepository.findById(goal.getAlignedOrgGoalId())
                    .orElseThrow(() -> new BusinessException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "Aligned objective was not found. goalId=" + goal.getGoalId()
                    ));
            if (objective.getOwnerType() != GoalOwnerType.ORGANIZATION) {
                throw new BusinessException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Aligned goal must be an objective. goalId=" + goal.getGoalId()
                );
            }
            if (isBlank(goal.getGradeSCriteria())
                    || isBlank(goal.getGradeACriteria())
                    || isBlank(goal.getGradeBCriteria())
                    || isBlank(goal.getGradeCCriteria())) {
                throw new BusinessException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Personal goal must define all S/A/B/C criteria before submission. goalId=" + goal.getGoalId()
                );
            }
        }

        UUID approverId = dto.getApproverId();
        if (approverId == null) {
            approverId = memberServiceClient.findDirectManagerId(companyId, requesterId);
            if (approverId == null) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "Approver could not be inferred automatically. Please specify approverId."
                );
            }
        }

        Optional<GoalApprovalBundle> latest = bundleRepository
                .findFirstByRequestedByAndCycleKeyOrderByRevisionDesc(requesterId, cycleKey);
        int nextRevision = latest.map(bundle -> bundle.getRevision() + 1).orElse(1);
        UUID originalBundleId = latest.map(GoalApprovalBundle::getBundleId).orElse(null);

        GoalApprovalBundle bundle = GoalApprovalBundle.builder()
                .companyId(companyId)
                .requestedBy(requesterId)
                .requestedAt(LocalDateTime.now())
                .cycleKey(cycleKey)
                .revision(nextRevision)
                .originalBundleId(originalBundleId)
                .weightSumSnapshot(sum)
                .status(BundleApprovalStatus.PENDING)
                .approverId(approverId)
                .goalIds(draftGoals.stream().map(Goal::getGoalId).collect(Collectors.toList()))
                .watcherIds(dto.getWatcherIds() != null ? new ArrayList<>(dto.getWatcherIds()) : new ArrayList<>())
                .build();

        GoalApprovalBundle saved = bundleRepository.save(bundle);
        draftGoals.forEach(Goal::markPending);
        notifyBundleRequested(saved);
        return BundleResDto.from(saved);
    }

    @Transactional
    public BundleResDto approve(UUID bundleId, UUID approverId, BundleApproveReqDto dto) {
        GoalApprovalBundle bundle = mustGet(bundleId);
        ensureApprover(bundle, approverId);

        bundle.approve(dto.getComment());
        goalRepository.findAllById(bundle.getGoalIds()).forEach(goal -> goal.activate(approverId));
        notifyBundleApproved(bundle, approverId);
        return BundleResDto.from(bundle);
    }

    @Transactional
    public BundleResDto reject(UUID bundleId, UUID approverId, BundleRejectReqDto dto) {
        GoalApprovalBundle bundle = mustGet(bundleId);
        ensureApprover(bundle, approverId);

        bundle.reject(dto.getReason(), dto.getAffectedGoalIds());
        goalRepository.findAllById(bundle.getGoalIds()).forEach(Goal::reject);
        notifyBundleRejected(bundle, approverId, dto.getReason());
        return BundleResDto.from(bundle);
    }

    @Transactional
    public BundleResDto withdraw(UUID bundleId, UUID requesterId) {
        GoalApprovalBundle bundle = mustGet(bundleId);
        if (!bundle.getRequestedBy().equals(requesterId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Only the requester can withdraw this bundle.");
        }
        bundle.withdraw();
        goalRepository.findAllById(bundle.getGoalIds()).forEach(Goal::withdraw);
        notifyBundleWithdrawn(bundle, requesterId);
        return BundleResDto.from(bundle);
    }

    @Transactional(readOnly = true)
    public BundleResDto get(UUID bundleId, UUID requesterId, UUID companyId) {
        GoalApprovalBundle bundle = mustGet(bundleId);
        if (!bundle.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bundle belongs to another company.");
        }

        boolean visible = bundle.getRequestedBy().equals(requesterId)
                || bundle.getApproverId().equals(requesterId)
                || bundle.getWatcherIds().contains(requesterId);
        if (!visible) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "You do not have permission to read this bundle.");
        }
        return BundleResDto.from(bundle);
    }

    @Transactional(readOnly = true)
    public List<BundleResDto> listMyRequested(UUID requesterId, UUID companyId) {
        return bundleRepository.findByRequestedByAndCompanyIdOrderByRequestedAtDesc(requesterId, companyId)
                .stream()
                .map(BundleResDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BundleResDto> listMyApprovalQueue(UUID approverId, UUID companyId) {
        return bundleRepository
                .findByApproverIdAndCompanyIdAndStatusOrderByRequestedAtDesc(
                        approverId, companyId, BundleApprovalStatus.PENDING)
                .stream()
                .map(BundleResDto::from)
                .collect(Collectors.toList());
    }

    private GoalApprovalBundle mustGet(UUID bundleId) {
        return bundleRepository.findById(bundleId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Approval bundle not found."));
    }

    private void ensureApprover(GoalApprovalBundle bundle, UUID actorId) {
        if (!bundle.getApproverId().equals(actorId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "You are not allowed to act on this bundle.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void notifyBundleRequested(GoalApprovalBundle bundle) {
        publishGoalBundleNotification(
                bundle.getApproverId(),
                bundle.getRequestedBy(),
                "목표 승인 요청이 도착했습니다. (" + bundle.getCycleKey() + ", 목표 " + bundle.getGoalIds().size() + "개)",
                bundle.getBundleId(),
                "GOAL_BUNDLE_REQUESTED"
        );
    }

    private void notifyBundleApproved(GoalApprovalBundle bundle, UUID actorId) {
        publishGoalBundleNotification(
                bundle.getRequestedBy(),
                actorId,
                "목표 승인 요청이 승인되었습니다. (" + bundle.getCycleKey() + ")",
                bundle.getBundleId(),
                "GOAL_BUNDLE_APPROVED"
        );
    }

    private void notifyBundleRejected(GoalApprovalBundle bundle, UUID actorId, String reason) {
        String suffix = isBlank(reason) ? "" : " 사유: " + reason;
        publishGoalBundleNotification(
                bundle.getRequestedBy(),
                actorId,
                "목표 승인 요청이 반려되었습니다. (" + bundle.getCycleKey() + ")" + suffix,
                bundle.getBundleId(),
                "GOAL_BUNDLE_REJECTED"
        );
    }

    private void notifyBundleWithdrawn(GoalApprovalBundle bundle, UUID requesterId) {
        publishGoalBundleNotification(
                bundle.getApproverId(),
                requesterId,
                "목표 승인 요청이 회수되었습니다. (" + bundle.getCycleKey() + ")",
                bundle.getBundleId(),
                "GOAL_BUNDLE_WITHDRAWN"
        );
    }

    private void publishGoalBundleNotification(UUID receiverId,
                                               UUID senderId,
                                               String content,
                                               UUID targetId,
                                               String targetType) {
        if (receiverId == null) return;
        eventPublisher.publishEvent(NotificationMessage.builder()
                .receiverId(receiverId)
                .senderId(senderId)
                // workforce-common:1.0.13 에서는 GOAL_EVALUATED 만 존재. 세부 이벤트는 targetType 으로 구분.
                .notificationType(NotificationType.GOAL_EVALUATED)
                .content(content)
                .targetId(targetId)
                .targetType(targetType)
                .build());
    }

    static CycleKeyParsed parseCycleKey(String cycleKey) {
        if (cycleKey == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "cycleKey is required.");
        }
        String key = cycleKey.endsWith("-PARTIAL")
                ? cycleKey.substring(0, cycleKey.length() - "-PARTIAL".length())
                : cycleKey;

        try {
            if (key.contains("-Q")) {
                String[] parts = key.split("-Q");
                int year = Integer.parseInt(parts[0]);
                int quarter = Integer.parseInt(parts[1]);
                int month = (quarter - 1) * 3 + 1;
                return new CycleKeyParsed(KpiCycle.QUARTERLY, LocalDate.of(year, month, 1));
            }
            if (key.contains("-H")) {
                String[] parts = key.split("-H");
                int year = Integer.parseInt(parts[0]);
                int half = Integer.parseInt(parts[1]);
                int month = half == 1 ? 1 : 7;
                return new CycleKeyParsed(KpiCycle.HALF_YEARLY, LocalDate.of(year, month, 1));
            }
            int year = Integer.parseInt(key);
            return new CycleKeyParsed(KpiCycle.YEARLY, LocalDate.of(year, 1, 1));
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid cycleKey format: " + cycleKey);
        }
    }

    static final class CycleKeyParsed {
        final KpiCycle cycle;
        final LocalDate start;

        CycleKeyParsed(KpiCycle cycle, LocalDate start) {
            this.cycle = cycle;
            this.start = start;
        }
    }
}
