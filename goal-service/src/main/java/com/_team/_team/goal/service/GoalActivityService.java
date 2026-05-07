package com._team._team.goal.service;

import com._team._team.dto.BusinessException;
import com._team._team.goal.domain.Goal;
import com._team._team.goal.domain.GoalActivity;
import com._team._team.goal.domain.enums.GoalActivityType;
import com._team._team.goal.dto.resdto.GoalActivityResDto;
import com._team._team.goal.repository.GoalActivityRepository;
import com._team._team.goal.repository.GoalRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GoalActivityService {

    private final GoalActivityRepository goalActivityRepository;
    private final GoalRepository goalRepository;
    private final GoalService goalService;

    public GoalActivityService(
            GoalActivityRepository goalActivityRepository,
            GoalRepository goalRepository,
            @Lazy GoalService goalService) {
        this.goalActivityRepository = goalActivityRepository;
        this.goalRepository = goalRepository;
        this.goalService = goalService;
    }

    @Transactional
    public void append(UUID goalId, GoalActivityType type, UUID actorId, String summary, String meta) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "목표를 찾을 수 없습니다. id=" + goalId));
        goalActivityRepository.save(
                GoalActivity.builder()
                        .goal(goal)
                        .type(type)
                        .actorId(actorId)
                        .summary(summary)
                        .metaJson(meta)
                        .build()
        );
    }

    @Transactional(readOnly = true)
    public List<GoalActivityResDto> list(UUID goalId, UUID requesterId, UUID companyId) {
        goalService.requireReadable(goalId, requesterId, companyId);
        return goalActivityRepository.findByGoal_GoalIdOrderByCreatedAtDesc(goalId)
                .stream()
                .map(GoalActivityResDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<GoalActivityResDto> listPage(UUID goalId, UUID requesterId, UUID companyId, int page, int size) {
        goalService.requireReadable(goalId, requesterId, companyId);
        return goalActivityRepository
                .findByGoal_GoalIdOrderByCreatedAtDesc(goalId, PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))))
                .map(GoalActivityResDto::from);
    }
}
