package com._team._team.salary.service;

import com._team._team.dto.BusinessException;
import com._team._team.event.RagSyncSalaryEvent;
import com._team._team.salary.publisher.RagSyncSalaryEventPublisher;
import com._team._team.salary.repository.SalaryPolicyRepository;
import com._team._team.salary.domain.SalaryPolicy;
import com._team._team.salary.dto.reqdto.SalaryPolicyCreateReqDto;
import com._team._team.salary.dto.reqdto.SalaryPolicyUpdateReqDto;
import com._team._team.salary.dto.resdto.SalaryPolicyResDto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;


@Service
@Transactional
public class SalaryPolicyService {

    private final SalaryPolicyRepository salaryPolicyRepository;
    private final RagSyncSalaryEventPublisher ragSyncSalaryEventPublisher;

    @Autowired
    public SalaryPolicyService(SalaryPolicyRepository salaryPolicyRepository,
                               RagSyncSalaryEventPublisher ragSyncSalaryEventPublisher) {
        this.salaryPolicyRepository = salaryPolicyRepository;
        this.ragSyncSalaryEventPublisher = ragSyncSalaryEventPublisher;
    }

    // 급여 정책 생성
    public SalaryPolicyResDto save(UUID companyId, SalaryPolicyCreateReqDto reqDto) {
        validateDateRange(reqDto.getEffectiveFrom(), reqDto.getEffectiveTo());

        // 기존 활성 정책이 있으면 신규 시작일 하루 전으로 자동 종료
        salaryPolicyRepository.findActivePolicies(companyId, LocalDate.now())
                .forEach(existing -> existing.close(reqDto.getEffectiveFrom().minusDays(1)));

        SalaryPolicy salaryPolicy = reqDto.toEntity(companyId);
        SalaryPolicy saved = salaryPolicyRepository.save(salaryPolicy);

        // RAG 동기화 이벤트 발행
        ragSyncSalaryEventPublisher.publish(
                RagSyncSalaryEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("CREATED")
                        .resourceType("SALARY_POLICY")
                        .resourceId(saved.getSalaryPolicyId())
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );

        return SalaryPolicyResDto.fromEntity(saved);
    }

    // 급여 정책 단건 조회
    @Transactional(readOnly = true)
    public SalaryPolicyResDto findById(UUID companyId, UUID salaryPolicyId) {
        SalaryPolicy salaryPolicy = salaryPolicyRepository.findById(salaryPolicyId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "급여 정책을 찾을 수 없습니다."));

        // 본인 회사만 조회 가능
        if (!salaryPolicy.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        return SalaryPolicyResDto.fromEntity(salaryPolicy);
    }

    // 회사별 급여 정책 목록 조회
    @Transactional(readOnly = true)
    public List<SalaryPolicyResDto> findByCompanyId(UUID companyId) {
        return salaryPolicyRepository.findByCompanyIdAndDelYn(companyId, "N").stream()
                .map(SalaryPolicyResDto::fromEntity)
                .toList();
    }

    // 급여 정책 수정
    public SalaryPolicyResDto update(UUID companyId, UUID salaryPolicyId, SalaryPolicyUpdateReqDto reqDto) {
        SalaryPolicy salaryPolicy = salaryPolicyRepository.findById(salaryPolicyId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "급여 정책을 찾을 수 없습니다."));

        // 본인 회사만 수정 가능
        if (!salaryPolicy.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        // 정산 완료된 과거 정책은 수정 불가
        if (salaryPolicy.isPast()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "정산이 완료된 과거 정책은 수정할 수 없습니다.");
        }

        // 현재 적용 중인 정책은 effectiveFrom 변경 불가 (종료일 조정만 허용)
        if (salaryPolicy.isActive() && !salaryPolicy.getEffectiveFrom().equals(reqDto.getEffectiveFrom())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "현재 적용 중인 정책은 적용 시작일을 변경할 수 없습니다.");
        }

        validateDateRange(reqDto.getEffectiveFrom(), reqDto.getEffectiveTo());

        salaryPolicy.update(reqDto);

        // RAG 동기화 이벤트 발행
        ragSyncSalaryEventPublisher.publish(
                RagSyncSalaryEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("UPDATED")
                        .resourceType("SALARY_POLICY")
                        .resourceId(salaryPolicyId)
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );

        return SalaryPolicyResDto.fromEntity(salaryPolicy);
    }

    // 급여 정책 삭제 (soft delete)
    public void delete(UUID companyId, UUID salaryPolicyId, boolean force) {
        SalaryPolicy salaryPolicy = salaryPolicyRepository.findById(salaryPolicyId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "급여 정책을 찾을 수 없습니다."));

        // 본인 회사만 삭제 가능
        if (!salaryPolicy.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        // force=false 일 때만 현재 적용/과거 정책 삭제 제한
        if (!force && salaryPolicy.isActive()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "현재 적용 중인 정책은 삭제할 수 없습니다.");
        }

        if (!force && salaryPolicy.isPast()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "정산이 완료된 과거 정책은 삭제할 수 없습니다.");
        }

        // 미래 정책만 soft delete 처리
        salaryPolicy.softDelete();

        // RAG 동기화 이벤트 발행
        ragSyncSalaryEventPublisher.publish(
                RagSyncSalaryEvent.builder()
                        .eventId(UUID.randomUUID())
                        .companyId(companyId)
                        .action("DELETED")
                        .resourceType("SALARY_POLICY")
                        .resourceId(salaryPolicyId)
                        .timestamp(Instant.now())
                        .triggeredBy("system")
                        .build()
        );
    }

    // 적용 기간 검증 - 월 단위 적용 강제
    private void validateDateRange(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "적용 종료일은 적용 시작일보다 빠를 수 없습니다.");
        }
        // 시작일은 매월 1일만
        if (effectiveFrom.getDayOfMonth() != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "급여 정책 적용 시작일은 매월 1일만 가능합니다. (월 중간 변경은 급여 계산 정합성을 깨뜨립니다)");
        }
        // 종료일은 매월 말일만 (null 허용 = 무기한 활성)
        if (effectiveTo != null) {
            LocalDate lastDayOfMonth = effectiveTo.withDayOfMonth(effectiveTo.lengthOfMonth());
            if (!effectiveTo.equals(lastDayOfMonth)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "급여 정책 적용 종료일은 매월 말일만 가능합니다. (예: 5월 종료라면 5/31)");
            }
        }
    }

}