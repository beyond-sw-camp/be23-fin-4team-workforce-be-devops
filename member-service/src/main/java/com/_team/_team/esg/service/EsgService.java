package com._team._team.esg.service;

import com._team._team.company.domain.Company;
import com._team._team.dto.BusinessException;
import com._team._team.dto.NotificationMessage;
import com._team._team.esg.domain.*;
import com._team._team.esg.domain.enums.*;
import com._team._team.esg.dtos.reqdto.*;
import com._team._team.esg.dtos.resdto.*;
import com._team._team.esg.repository.*;
import com._team._team.member.domain.Member;
import com._team._team.member.repository.MemberRepository;
import com._team._team.notification.NotificationType;
import com._team._team.s3.S3Uploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class EsgService {

    private final MemberRepository memberRepository;
    private final EsgCompanyConfigRepository configRepository;
    private final EsgActivitySubjectRepository subjectRepository;
    private final EsgActivityRepository activityRepository;
    private final EsgPointHistoryRepository pointHistoryRepository;
    private final EsgScoreRepository scoreRepository;
    private final EsgShopItemRepository shopItemRepository;
    private final EsgShopOrderRepository shopOrderRepository;
    private final S3Uploader s3Uploader;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public EsgService(MemberRepository memberRepository, EsgCompanyConfigRepository configRepository, EsgActivitySubjectRepository subjectRepository, EsgActivityRepository activityRepository, EsgPointHistoryRepository pointHistoryRepository, EsgScoreRepository scoreRepository, EsgShopItemRepository shopItemRepository, EsgShopOrderRepository shopOrderRepository, S3Uploader s3Uploader, ApplicationEventPublisher eventPublisher) {
        this.memberRepository = memberRepository;
        this.configRepository = configRepository;
        this.subjectRepository = subjectRepository;
        this.activityRepository = activityRepository;
        this.pointHistoryRepository = pointHistoryRepository;
        this.scoreRepository = scoreRepository;
        this.shopItemRepository = shopItemRepository;
        this.shopOrderRepository = shopOrderRepository;
        this.s3Uploader = s3Uploader;
        this.eventPublisher = eventPublisher;
    }

    // ═══════════════════════════════════════════════════════════
    // 설정
    // ═══════════════════════════════════════════════════════════

    public void createDefaultConfig(Company company) {
        if (configRepository.existsByCompany(company)) return;
        configRepository.save(EsgCompanyConfig.builder()
                .company(company)
                .build());
    }

    @Transactional(readOnly = true)
    public EsgConfigResDto getConfig(UUID memberId) {
        Member member = getMember(memberId);
        return EsgConfigResDto.fromEntity(getConfig(member.getCompany()));
    }

    public void updateConfig(UUID memberId, EsgConfigUpdateReqDto reqDto) {
        Member member = getMember(memberId);
        EsgCompanyConfig config = getConfig(member.getCompany());
        config.update(
                reqDto.getEsgEnabledYn(),
                reqDto.getMonthlyPointLimit()
        );
    }

    // ═══════════════════════════════════════════════════════════
    // 활동 양식
    // ═══════════════════════════════════════════════════════════

    public UUID createSubject(UUID memberId, EsgSubjectCreateReqDto reqDto) {
        Member member = getMember(memberId);
        validateEsgEnabled(member.getCompany());

        EsgActivitySubject subject = EsgActivitySubject.builder()
                .company(member.getCompany())
                .title(reqDto.getTitle())
                .description(reqDto.getDescription())
                .category(reqDto.getCategory())
                .defaultPoints(reqDto.getDefaultPoints())
                .build();

        return subjectRepository.save(subject).getEsgActivitySubjectId();
    }

    @Transactional(readOnly = true)
    public List<EsgSubjectResDto> getSubjectList(UUID memberId) {
        Member member = getMember(memberId);
        validateEsgEnabled(member.getCompany());

        return subjectRepository
                .findByCompanyAndDelYn(member.getCompany(), "NO")
                .stream()
                .map(EsgSubjectResDto::fromEntity)
                .toList();
    }

    public void updateSubject(UUID memberId, UUID subjectId, EsgSubjectCreateReqDto reqDto) {
        Member member = getMember(memberId);
        EsgActivitySubject subject = getSubject(subjectId, member.getCompany());
        subject.update(
                reqDto.getTitle(),
                reqDto.getDescription(),
                reqDto.getCategory(),
                reqDto.getDefaultPoints()
        );
    }

    public void deleteSubject(UUID memberId, UUID subjectId) {
        Member member = getMember(memberId);
        getSubject(subjectId, member.getCompany()).delete();
    }

    // ═══════════════════════════════════════════════════════════
    // 활동 인증
    // ═══════════════════════════════════════════════════════════

    public UUID submitActivity(UUID memberId, UUID subjectId,
                               String verificationContent, MultipartFile file) {
        Member member = getMember(memberId);
        validateEsgEnabled(member.getCompany());

        EsgActivitySubject subject = getSubject(subjectId, member.getCompany());

        if ((verificationContent == null || verificationContent.isBlank())
                && (file == null || file.isEmpty())) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "활동 내용 또는 첨부 파일을 입력해주세요.");
        }

        String fileUrl = null;
        if (file != null && !file.isEmpty()) {
            fileUrl = s3Uploader.upload(file, "esg/activity");
        }

        EsgActivity activity = EsgActivity.builder()
                .member(member)
                .subject(subject)
                .category(subject.getCategory())
                .verificationContent(verificationContent)
                .fileUrl(fileUrl)
                .build();

        return activityRepository.save(activity).getEsgActivityId();
    }

    @Transactional(readOnly = true)
    public List<EsgActivityResDto> getActivityList(UUID memberId, ActivityStatus status) {
        Member member = getMember(memberId);
        validateEsgEnabled(member.getCompany());

        List<EsgActivity> activities = (status != null)
                ? activityRepository.findAllByCompanyIdAndStatus(
                member.getCompany().getCompanyId(), status)
                : activityRepository.findAllByCompanyId(
                member.getCompany().getCompanyId());

        return activities.stream().map(EsgActivityResDto::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<EsgActivityResDto> getMyActivityList(UUID memberId, ActivityStatus status) {
        Member member = getMember(memberId);
        validateEsgEnabled(member.getCompany());

        List<EsgActivity> activities = (status != null)
                ? activityRepository.findByMemberAndStatusOrderByCreatedAtDesc(member, status)
                : activityRepository.findByMemberOrderByCreatedAtDesc(member);

        return activities.stream().map(EsgActivityResDto::fromEntity).toList();
    }

    public void approveActivity(UUID memberId, UUID activityId) {
        Member approver = getMember(memberId);
        validateEsgEnabled(approver.getCompany());

        EsgActivity activity = getActivity(activityId, approver.getCompany());

        if (!activity.isPending()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "이미 처리된 활동입니다.");
        }

        int points = activity.getSubject().getDefaultPoints();
        activity.approve(approver.getMemberId(), points);

        // ESG ON 이면 포인트 자동 적립
        earnPoint(
                activity.getMember(),
                ReferenceType.ACTIVITY,
                activity.getEsgActivityId(),
                points,
                activity.getSubject().getTitle() + " 활동 승인"
        );

        eventPublisher.publishEvent(NotificationMessage.builder()
                .receiverId(activity.getMember().getMemberId())
                .senderId(approver.getMemberId())
                .notificationType(NotificationType.ESG_ACTIVITY_APPROVED)
                .content(activity.getSubject().getTitle()
                        + " 활동이 승인됐습니다. +" + points + "P")
                .targetId(activity.getEsgActivityId())
                .targetType("ESG_ACTIVITY")
                .build());
    }

    public void rejectActivity(UUID memberId, UUID activityId, EsgRejectReqDto reqDto) {
        Member approver = getMember(memberId);
        validateEsgEnabled(approver.getCompany());

        EsgActivity activity = getActivity(activityId, approver.getCompany());

        if (!activity.isPending()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "이미 처리된 활동입니다.");
        }

        activity.reject(approver.getMemberId(), reqDto.getReason());

        eventPublisher.publishEvent(NotificationMessage.builder()
                .receiverId(activity.getMember().getMemberId())
                .senderId(approver.getMemberId())
                .notificationType(NotificationType.ESG_ACTIVITY_REJECTED)
                .content(activity.getSubject().getTitle()
                        + " 활동이 반려됐습니다. 사유: " + reqDto.getReason())
                .targetId(activity.getEsgActivityId())
                .targetType("ESG_ACTIVITY")
                .build());
    }

    // ═══════════════════════════════════════════════════════════
    // 포인트
    // ═══════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public int getCurrentBalance(UUID memberId) {
        Member member = getMember(memberId);
        return pointHistoryRepository.findLatestByMember(member)
                .map(EsgPointHistory::getBalance)
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public List<EsgPointHistoryResDto> getPointHistory(UUID memberId) {
        Member member = getMember(memberId);
        return pointHistoryRepository.findByMemberOrderByCreatedAtDesc(member)
                .stream()
                .map(EsgPointHistoryResDto::fromEntity)
                .toList();
    }

    // ═══════════════════════════════════════════════════════════
    // 포인트샵
    // ═══════════════════════════════════════════════════════════

    public UUID createShopItem(UUID memberId, EsgShopItemCreateReqDto reqDto,
                               MultipartFile image) {
        Member member = getMember(memberId);
        validateEsgEnabled(member.getCompany());

        String imageUrl = null;
        if (image != null && !image.isEmpty()) {
            imageUrl = s3Uploader.upload(image, "esg/shop");
        }

        EsgShopItem item = EsgShopItem.builder()
                .company(member.getCompany())
                .title(reqDto.getTitle())
                .description(reqDto.getDescription())
                .imageUrl(imageUrl)
                .requiredPoints(reqDto.getRequiredPoints())
                .stock(reqDto.getStock())
                .build();

        return shopItemRepository.save(item).getEsgShopItemId();
    }

    @Transactional(readOnly = true)
    public List<EsgShopItemResDto> getShopItemList(UUID memberId) {
        Member member = getMember(memberId);
        validateEsgEnabled(member.getCompany());

        return shopItemRepository
                .findByCompanyAndDelYn(member.getCompany(), "NO")
                .stream()
                .map(EsgShopItemResDto::fromEntity)
                .toList();
    }

    public void updateShopItem(UUID memberId, UUID itemId,
                               EsgShopItemCreateReqDto reqDto, MultipartFile image) {
        Member member = getMember(memberId);
        EsgShopItem item = getShopItem(itemId, member.getCompany());

        String imageUrl = item.getImageUrl();
        if (image != null && !image.isEmpty()) {
            if (imageUrl != null) s3Uploader.delete(imageUrl);
            imageUrl = s3Uploader.upload(image, "esg/shop");
        }

        item.update(reqDto.getTitle(), reqDto.getDescription(),
                imageUrl, reqDto.getRequiredPoints(), reqDto.getStock());
    }

    public void deleteShopItem(UUID memberId, UUID itemId) {
        Member member = getMember(memberId);
        EsgShopItem item = getShopItem(itemId, member.getCompany());

        if (item.getImageUrl() != null) {
            s3Uploader.delete(item.getImageUrl());
        }
        item.delete();
    }

    public UUID orderShopItem(UUID memberId, UUID itemId) {
        Member member = getMember(memberId);
        validateEsgEnabled(member.getCompany());

        EsgShopItem item = getShopItem(itemId, member.getCompany());

        if (item.getStock() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "품절된 물품입니다.");
        }

        int currentBalance = getCurrentBalance(memberId);
        if (currentBalance < item.getRequiredPoints()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "포인트가 부족합니다. 현재 잔액: " + currentBalance
                            + "P, 필요: " + item.getRequiredPoints() + "P");
        }

        item.decreaseStock();

        EsgShopOrder order = EsgShopOrder.builder()
                .member(member)
                .shopItem(item)
                .usedPoints(item.getRequiredPoints())
                .build();

        shopOrderRepository.save(order);

        usePoint(
                member,
                ReferenceType.SHOP_ORDER,
                order.getEsgShopOrderId(),
                item.getRequiredPoints(),
                item.getTitle() + " 구매"
        );

        eventPublisher.publishEvent(NotificationMessage.builder()
                .receiverId(member.getMemberId())
                .notificationType(NotificationType.ESG_SHOP_ORDER_COMPLETE)
                .content(item.getTitle() + " 구매가 완료됐습니다. -"
                        + item.getRequiredPoints() + "P")
                .targetId(order.getEsgShopOrderId())
                .targetType("ESG_SHOP_ORDER")
                .build());

        return order.getEsgShopOrderId();
    }

    @Transactional(readOnly = true)
    public List<EsgShopOrderResDto> getMyShopOrders(UUID memberId) {
        Member member = getMember(memberId);
        return shopOrderRepository.findByMemberOrderByCreatedAtDesc(member)
                .stream()
                .map(EsgShopOrderResDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EsgShopOrderResDto> getAllShopOrders(UUID memberId) {
        Member member = getMember(memberId);
        return shopOrderRepository.findByCompanyOrderByCreatedAtDesc(member.getCompany())
                .stream()
                .map(EsgShopOrderResDto::fromEntity)
                .toList();
    }

    // ═══════════════════════════════════════════════════════════
    // 점수
    // ═══════════════════════════════════════════════════════════

    public EsgScoreResDto calculateMyMonthlyScore(UUID memberId, String yearMonth) {
        Member member = getMember(memberId);
        validateEsgEnabled(member.getCompany());

        List<EsgActivity> activities = activityRepository
                .findApprovedByMemberAndYearMonth(member, yearMonth);

        int e = sumScore(activities, EsgCategory.E);
        int s = sumScore(activities, EsgCategory.S);
        int g = sumScore(activities, EsgCategory.G);

        EsgScore score = scoreRepository
                .findByMemberAndYearMonth(member, yearMonth)
                .map(existing -> {
                    scoreRepository.delete(existing);
                    return EsgScore.snapshot(
                            member.getCompany().getCompanyId(), member, yearMonth, e, s, g);
                })
                .orElse(EsgScore.snapshot(
                        member.getCompany().getCompanyId(), member, yearMonth, e, s, g));

        EsgScore saved = scoreRepository.save(score);
        member.updateEsgScore(saved.getTotalScore());

        return EsgScoreResDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<EsgScoreResDto> getMyScoreHistory(UUID memberId) {
        Member member = getMember(memberId);
        return scoreRepository.findByMemberOrderByYearMonthDesc(member)
                .stream()
                .map(EsgScoreResDto::fromEntity)
                .toList();
    }

    // ═══════════════════════════════════════════════════════════
    // Private 헬퍼
    // ═══════════════════════════════════════════════════════════

    private void earnPoint(Member member, ReferenceType referenceType,
                           UUID referenceId, int points, String description) {
        validateMonthlyLimit(member, points);

        int currentBalance = pointHistoryRepository.findLatestByMember(member)
                .map(EsgPointHistory::getBalance)
                .orElse(0);

        pointHistoryRepository.save(
                EsgPointHistory.earn(member, referenceType, referenceId,
                        points, currentBalance + points, description)
        );

        eventPublisher.publishEvent(NotificationMessage.builder()
                .receiverId(member.getMemberId())
                .notificationType(NotificationType.ESG_POINT_EARNED)
                .content(description + " +" + points + "P 적립됐습니다. 잔액: "
                        + (currentBalance + points) + "P")
                .targetId(referenceId)
                .targetType(referenceType.name())
                .build());
    }

    private void usePoint(Member member, ReferenceType referenceType,
                          UUID referenceId, int points, String description) {
        int currentBalance = pointHistoryRepository.findLatestByMember(member)
                .map(EsgPointHistory::getBalance)
                .orElse(0);

        if (currentBalance < points) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "포인트가 부족합니다.");
        }

        pointHistoryRepository.save(
                EsgPointHistory.use(member, referenceType, referenceId,
                        points, currentBalance - points, description)
        );
    }

    private void validateMonthlyLimit(Member member, int points) {
        EsgCompanyConfig config = getConfig(member.getCompany());

        String currentYearMonth = YearMonth.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM"));

        int monthlyEarned = pointHistoryRepository
                .findMonthlyEarnedByMember(member, currentYearMonth);

        if (monthlyEarned + points > config.getMonthlyPointLimit()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "월 포인트 적립 한도를 초과합니다. 이번 달 잔여 적립 가능: "
                            + (config.getMonthlyPointLimit() - monthlyEarned) + "P");
        }
    }

    private Member getMember(UUID memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."));
    }

    private EsgCompanyConfig getConfig(Company company) {
        return configRepository.findByCompany(company)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "ESG 설정 정보를 찾을 수 없습니다."));
    }

    private void validateEsgEnabled(Company company) {
        if (!getConfig(company).isEsgEnabled()) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "ESG 그린장터가 활성화되지 않은 회사입니다.");
        }
    }

    private EsgActivitySubject getSubject(UUID subjectId, Company company) {
        EsgActivitySubject subject = subjectRepository
                .findByEsgActivitySubjectIdAndDelYn(subjectId, "NO")
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 활동 양식입니다."));

        if (!subject.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "다른 회사의 활동 양식입니다.");
        }
        return subject;
    }

    private EsgActivity getActivity(UUID activityId, Company company) {
        EsgActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 활동입니다."));

        if (!activity.getMember().getCompany().getCompanyId()
                .equals(company.getCompanyId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "다른 회사의 활동입니다.");
        }
        return activity;
    }

    private EsgShopItem getShopItem(UUID itemId, Company company) {
        EsgShopItem item = shopItemRepository
                .findByEsgShopItemIdAndDelYn(itemId, "NO")
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "존재하지 않는 물품입니다."));

        if (!item.getCompany().getCompanyId().equals(company.getCompanyId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "다른 회사의 물품입니다.");
        }
        return item;
    }

    private int sumScore(List<EsgActivity> activities, EsgCategory category) {
        return activities.stream()
                .filter(a -> a.getCategory() == category)
                .mapToInt(a -> a.getEarnedPoints() != null ? a.getEarnedPoints() : 0)
                .sum();
    }
}