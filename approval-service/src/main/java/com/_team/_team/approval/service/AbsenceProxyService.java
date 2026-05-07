package com._team._team.approval.service;

import com._team._team.approval.domain.AbsenceProxy;
import com._team._team.approval.dto.reqdto.AbsenceProxyCreateReqDto;
import com._team._team.approval.dto.resdto.AbsenceProxyResDto;
import com._team._team.approval.repository.AbsenceProxyRepository;
import com._team._team.dto.BusinessException;
import com._team._team.dto.NotificationMessage;
import com._team._team.notification.NotificationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AbsenceProxyService {

    private final AbsenceProxyRepository absenceProxyRepository;
    private final ApplicationEventPublisher eventPublisher;


    @Autowired
    public AbsenceProxyService(AbsenceProxyRepository absenceProxyRepository, ApplicationEventPublisher eventPublisher) {
        this.absenceProxyRepository = absenceProxyRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 부재 위임 등록
     * 검증: ① 자기 위임 ② 기간 유효성 ③ 기간 중복 ④ 체인 위임
     */
    public AbsenceProxyResDto create(UUID companyId, UUID memberId,
                                     AbsenceProxyCreateReqDto reqDto) {

        // ① 자기 자신 위임 방지
        if (memberId.equals(reqDto.getSubstituteId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "본인에게 위임할 수 없습니다.");
        }

        // ② 기간 유효성
        LocalDate today = LocalDate.now();
        LocalDate startDateOnly = reqDto.getStartDate().toLocalDate();

        if (startDateOnly.isBefore(today)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "위임 시작일은 오늘 이후여야 합니다.");
        }

        if (reqDto.getEndDate().isBefore(reqDto.getStartDate())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "종료일은 시작일 이후여야 합니다.");
        }

        // ③ 본인 기간 중복 방지
        List<AbsenceProxy> overlapping = absenceProxyRepository.findOverlapping(
                companyId, memberId, reqDto.getStartDate(), reqDto.getEndDate());

        if (!overlapping.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "해당 기간에 이미 위임이 등록되어 있습니다.");
        }

        // ④ 체인 위임 방지 (대결자가 같은 기간에 부재자인지)
        List<AbsenceProxy> substituteAbsence = absenceProxyRepository.findSubstituteAbsence(
                companyId, reqDto.getSubstituteId(), reqDto.getStartDate(), reqDto.getEndDate());

        if (!substituteAbsence.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "해당 대결자도 같은 기간에 부재 중이라 위임할 수 없습니다.");
        }

        // 저장
        AbsenceProxy proxy = AbsenceProxy.builder()
                .companyId(companyId)
                .memberId(memberId)
                .substituteId(reqDto.getSubstituteId())
                .startDate(reqDto.getStartDate())
                .endDate(reqDto.getEndDate())
                .build();

        AbsenceProxy saved = absenceProxyRepository.save(proxy);

        // 수임자에게 알림
        eventPublisher.publishEvent(NotificationMessage.builder()
                .receiverId(reqDto.getSubstituteId())
                .senderId(memberId)
                .notificationType(NotificationType.APPROVAL_PROXY_ASSIGNED)
                .content("결재 위임이 등록되었습니다. ("
                        + reqDto.getStartDate().toLocalDate() + " ~ "
                        + reqDto.getEndDate().toLocalDate() + ")")
                .targetId(saved.getProxyId())
                .targetType("APPROVAL_PROXY")
                .build());

        return AbsenceProxyResDto.fromEntity(saved);
    }

    /**
     * 내가 설정한 위임 목록
     * 기간 지난 건은 자동 제외
     */
    @Transactional(readOnly = true)
    public List<AbsenceProxyResDto> findMyProxies(UUID companyId, UUID memberId) {
        LocalDateTime now = LocalDateTime.now();
        return absenceProxyRepository.findCurrentAndFutureByMemberId(companyId, memberId, now)
                .stream()
                .map(AbsenceProxyResDto::fromEntity)
                .toList();
    }

    /**
     * 내가 대결자로 지정된 목록 (현재 진행 중 + 미래 예약)
     */
    @Transactional(readOnly = true)
    public List<AbsenceProxyResDto> findDelegatedToMe(UUID companyId, UUID memberId) {
        LocalDateTime now = LocalDateTime.now();
        return absenceProxyRepository.findCurrentAndFutureBySubstituteId(companyId, memberId, now)
                .stream()
                .map(AbsenceProxyResDto::fromEntity)
                .toList();
    }

    /**
     * 위임 비활성화 (수동 취소)
     */
    public AbsenceProxyResDto deactivate(UUID companyId, UUID memberId, UUID proxyId) {
        AbsenceProxy proxy = absenceProxyRepository.findById(proxyId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "위임 설정을 찾을 수 없습니다."));

        if (!proxy.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        if (!proxy.getMemberId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "본인의 위임 설정만 취소할 수 있습니다.");
        }

        if ("N".equals(proxy.getIsActiveYn())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "이미 비활성화된 위임입니다.");
        }

        proxy.deactivate();
        return AbsenceProxyResDto.fromEntity(proxy);
    }
}
