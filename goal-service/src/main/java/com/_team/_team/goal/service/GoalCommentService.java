package com._team._team.goal.service;

import com._team._team.dto.BusinessException;
import com._team._team.goal.domain.Goal;
import com._team._team.goal.domain.GoalComment;
import com._team._team.goal.domain.enums.GoalActivityType;
import com._team._team.goal.dto.reqdto.GoalCommentReactionReqDto;
import com._team._team.goal.dto.reqdto.GoalCommentReqDto;
import com._team._team.goal.dto.resdto.GoalCommentResDto;
import com._team._team.goal.repository.GoalCommentRepository;
import com._team._team.goal.repository.GoalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GoalCommentService {

    private final GoalRepository goalRepository;
    private final GoalCommentRepository goalCommentRepository;
    private final GoalActivityService goalActivityService;
    private final GoalService goalService;

    public GoalCommentService(
            GoalRepository goalRepository,
            GoalCommentRepository goalCommentRepository,
            GoalActivityService goalActivityService,
            GoalService goalService) {
        this.goalRepository = goalRepository;
        this.goalCommentRepository = goalCommentRepository;
        this.goalActivityService = goalActivityService;
        this.goalService = goalService;
    }

    @Transactional
    public GoalCommentResDto create(UUID goalId, GoalCommentReqDto dto, UUID authorId, UUID companyId) {
        Goal goal = loadGoal(goalId, companyId);
        GoalComment c = goalCommentRepository.save(
                GoalComment.builder()
                        .goal(goal)
                        .authorId(authorId)
                        .body(dto.getBody())
                        .build());
        goalActivityService.append(goalId, GoalActivityType.COMMENT_ADDED, authorId, "댓글 작성", null);
        return GoalCommentResDto.from(c);
    }

    @Transactional(readOnly = true)
    public List<GoalCommentResDto> list(UUID goalId, UUID requesterId, UUID companyId) {
        goalService.requireReadable(goalId, requesterId, companyId);
        return goalCommentRepository.findByGoal_GoalIdOrderByCreatedAtAsc(goalId).stream()
                .map(GoalCommentResDto::from)
                .toList();
    }

    @Transactional
    public GoalCommentResDto update(UUID goalId, UUID commentId, GoalCommentReqDto dto, UUID memberId, UUID companyId) {
        GoalComment c = loadComment(goalId, commentId, companyId);
        if (!c.getAuthorId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "본인 댓글만 수정할 수 있습니다.");
        }
        c.updateBody(dto.getBody());
        return GoalCommentResDto.from(c);
    }

    @Transactional
    public void delete(UUID goalId, UUID commentId, UUID memberId, UUID companyId) {
        GoalComment c = loadComment(goalId, commentId, companyId);
        if (!c.getAuthorId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "본인 댓글만 삭제할 수 있습니다.");
        }
        goalCommentRepository.delete(c);
    }

    @Transactional
    public GoalCommentResDto toggleReaction(
            UUID goalId, UUID commentId, GoalCommentReactionReqDto dto, UUID companyId) {
        GoalComment c = loadComment(goalId, commentId, companyId);
        c.toggleReaction(dto.getEmoji(), dto.getMemberId());
        goalActivityService.append(goalId, GoalActivityType.COMMENT_REACTION, dto.getMemberId(), "리액션", null);
        return GoalCommentResDto.from(c);
    }

    private Goal loadGoal(UUID goalId, UUID companyId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "목표를 찾을 수 없습니다."));
        if (!goal.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "다른 회사의 목표입니다.");
        }
        return goal;
    }

    private GoalComment loadComment(UUID goalId, UUID commentId, UUID companyId) {
        GoalComment c = goalCommentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."));
        if (!c.getGoal().getGoalId().equals(goalId) || !c.getGoal().getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "댓글이 목표에 속하지 않습니다.");
        }
        return c;
    }
}
