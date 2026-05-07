package com._team._team.salary.service;

import com._team._team.dto.BusinessException;
import com._team._team.salary.domain.PayGradeTable;
import com._team._team.salary.domain.Salary;
import com._team._team.salary.domain.SalaryPolicy;
import com._team._team.salary.domain.enums.TaxReductionType;
import com._team._team.salary.dto.reqdto.SalaryCreateReqDto;
import com._team._team.salary.dto.reqdto.SalaryUpdateReqDto;
import com._team._team.salary.dto.resdto.PayrollPrecheckResDto;
import com._team._team.salary.dto.resdto.SalaryResDto;
import com._team._team.salary.feignClients.MemberFeignClient;
import com._team._team.salary.feignClients.dto.MemberResDto;
import com._team._team.salary.repository.MemberAllowanceRepository;
import com._team._team.salary.repository.PayGradeTableRepository;
import com._team._team.salary.repository.SalaryPolicyRepository;
import com._team._team.salary.repository.SalaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class SalaryService {
    private static final int STANDARD_MONTHLY_WORK_HOURS = 209;
    /** 연도별 시급 기준 최저임금 (원) , 미등록 연도는 직전 연도 값 사용 */
    private static final NavigableMap<Integer, Long> MINIMUM_WAGE_HOURLY_BY_YEAR = new TreeMap<>(
            Map.of(
                    2024, 9860L,
                    2025, 10030L
            )
    );

    private final SalaryRepository salaryRepository;
    private final SalaryPolicyRepository salaryPolicyRepository;
    private final PayGradeTableRepository payGradeTableRepository;
    private final MemberAllowanceRepository memberAllowanceRepository;
    private final MemberFeignClient memberFeignClient;

    @Autowired
    public SalaryService(SalaryRepository salaryRepository,
                         SalaryPolicyRepository salaryPolicyRepository,
                         PayGradeTableRepository payGradeTableRepository,
                         MemberAllowanceRepository memberAllowanceRepository,
                         MemberFeignClient memberFeignClient){
        this.salaryRepository = salaryRepository;
        this.salaryPolicyRepository = salaryPolicyRepository;
        this.payGradeTableRepository = payGradeTableRepository;
        this.memberAllowanceRepository = memberAllowanceRepository;
        this.memberFeignClient = memberFeignClient;
    }

    // 급여 생성
    public SalaryResDto createSalary(UUID companyId, SalaryCreateReqDto reqDto) {
        // 1. 적용일 기준 회사 활성 정책 조회
        SalaryPolicy policy = salaryPolicyRepository
                .findActivePolicies(companyId, reqDto.getEffectiveFrom())
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "활성 급여 정책이 없습니다."));

        // 적용 기간 검증
        validateDateRange(reqDto.getEffectiveFrom(), reqDto.getEffectiveTo());

        // 이전 급여가 있었을 경우, 이전 급여의 effectiveTo를 자동으로 닫아줌
        // 새 급여 시작일 전날로 이전 급여 종료
        // 같은 effectiveFrom 으로 등록 요청이 오면 기존 행을 닫는 로직이 effectiveTo=시작일-1 로
        Salary prevSalaryRef = salaryRepository
                .findActiveSalary(reqDto.getMemberId(), companyId, reqDto.getEffectiveFrom())
                .orElse(null);
        if (prevSalaryRef != null) {
            if (prevSalaryRef.getEffectiveFrom() != null
                    && prevSalaryRef.getEffectiveFrom().equals(reqDto.getEffectiveFrom())) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "이미 같은 적용 시작일(" + reqDto.getEffectiveFrom()
                                + ")로 등록된 급여가 있습니다. 기존 이력을 수정하거나 다른 시작일을 사용하세요.");
            }
            prevSalaryRef.closeEffectivePeriod(reqDto.getEffectiveFrom().minusDays(1));
        }

        // 호봉제 여부에 따라 step 필수 검증
        if ("Y".equals(policy.getUsePayGradeYn()) && reqDto.getStep() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "호봉제 정책입니다. 호봉을 지정하세요.");
        }

        long resolvedBaseSalary = resolveBaseSalary(
                companyId, reqDto.getStep(), reqDto.getBaseSalary(), reqDto.getEffectiveFrom());
        validateMinimumWage(resolvedBaseSalary, reqDto.getEffectiveFrom());

        // 직급/직책 - member-service 인사정보에서 lookup
        MemberResDto m = fetchMemberMap(companyId).get(reqDto.getMemberId());
        String resolvedJobGradeName = m != null ? m.getJobGradeName() : null;
        String resolvedJobTitleName = m != null ? m.getJobTitleName() : null;

        // 부양가족수
        Integer resolvedDependentCount = reqDto.getDependentCount();

        // 자녀수, 감면
        Integer resolvedChildUnder20Count = reqDto.getChildUnder20Count() == null
                ? 0
                : reqDto.getChildUnder20Count();
        TaxReductionType resolvedReductionType = reqDto.getTaxReductionType() == null
                ? TaxReductionType.NONE
                : reqDto.getTaxReductionType();

        Salary salary = Salary.builder()
                .memberId(reqDto.getMemberId())
                .companyId(companyId)
                .salaryPolicyId(policy.getSalaryPolicyId())
                .baseSalary(resolvedBaseSalary)
                .step(reqDto.getStep())
                .jobGradeName(resolvedJobGradeName)
                .jobTitleName(resolvedJobTitleName)
                .effectiveFrom(reqDto.getEffectiveFrom())
                .effectiveTo(reqDto.getEffectiveTo())
                .dependentCount(resolvedDependentCount)
                .childUnder20Count(resolvedChildUnder20Count)
                .taxReductionType(resolvedReductionType)
                .taxReductionRate(reqDto.getTaxReductionRate())
                .taxReductionEffectiveTo(reqDto.getTaxReductionEffectiveTo())
                .build();

        Salary saved = salaryRepository.save(salary);
        return SalaryResDto.fromEntity(saved);
    }

    // 급여 단건 조회
    @Transactional(readOnly = true)
    public SalaryResDto findById(UUID companyId, UUID salaryId){
        Salary salary = salaryRepository.findById(salaryId)
                .orElseThrow(()-> new BusinessException(HttpStatus.NOT_FOUND,"급여 정보를 찾을 수 없습니다."));

        if (!salary.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        return SalaryResDto.fromEntity(salary);
    }

    // 회사별 급여 목록 조회 직원 정보 결합 사번 이름 부서명 채움
    @Transactional(readOnly = true)
    public List<SalaryResDto> findByCompanyId(UUID companyId){
        List<Salary> salaries = salaryRepository.findByCompanyId(companyId);
        if (salaries.isEmpty()) return Collections.emptyList();

        Map<UUID, MemberResDto> memberMap = fetchMemberMap(companyId);

        return salaries.stream()
                .map(s -> {
                    MemberResDto m = memberMap.get(s.getMemberId());
                    String sabun  = m != null ? m.getSabun() : null;
                    String name   = m != null ? m.getName() : null;
                    String orgNm  = m != null ? m.getOrganizationName() : null;
                    return SalaryResDto.fromEntity(s, sabun, name, orgNm);
                })
                .toList();
    }

    /**
     * 급여대장 생성 직전 사전 검증 - 활성 Salary 미등록 직원 + 계좌 미등록 직원을 분리 반환
     * 정산 시 0원/이체 누락 사고를 사전에 막기 위한 화면 알림용
     */
    @Transactional(readOnly = true)
    public PayrollPrecheckResDto precheckPayroll(UUID companyId) {
        Map<UUID, MemberResDto> memberMap = fetchMemberMap(companyId);
        LocalDate today = LocalDate.now();

        // 회사의 활성 Salary 매핑 - memberId -> 활성 baseSalary>0 행이 있으면 OK
        Set<UUID> hasActiveSalary = salaryRepository.findByCompanyId(companyId).stream()
                .filter(s -> {
                    if (s.getEffectiveFrom() == null) return false;
                    boolean started = !s.getEffectiveFrom().isAfter(today);
                    boolean notEnded = s.getEffectiveTo() == null || !s.getEffectiveTo().isBefore(today);
                    boolean hasBase = s.getBaseSalary() != null && s.getBaseSalary() > 0;
                    return started && notEnded && hasBase;
                })
                .map(Salary::getMemberId)
                .collect(Collectors.toSet());

        List<PayrollPrecheckResDto.MemberRef> missingSalary = new ArrayList<>();
        List<PayrollPrecheckResDto.MemberRef> missingBank = new ArrayList<>();

        for (MemberResDto m : memberMap.values()) {
            if (!hasActiveSalary.contains(m.getMemberId())) {
                missingSalary.add(buildMemberRef(m));
            }
            if (m.getBank() == null || m.getBank().isBlank()
                    || m.getBankAccount() == null || m.getBankAccount().isBlank()) {
                missingBank.add(buildMemberRef(m));
            }
        }

        return PayrollPrecheckResDto.builder()
                .totalActiveMembers(memberMap.size())
                .missingSalaryCount(missingSalary.size())
                .missingSalary(missingSalary)
                .missingBankAccountCount(missingBank.size())
                .missingBankAccount(missingBank)
                .build();
    }

    private PayrollPrecheckResDto.MemberRef buildMemberRef(MemberResDto m) {
        return PayrollPrecheckResDto.MemberRef.builder()
                .memberId(m.getMemberId())
                .name(m.getName())
                .sabun(m.getSabun())
                .organizationName(m.getOrganizationName())
                .build();
    }

    // 회사 직원 정보 일괄 조회
    private Map<UUID, MemberResDto> fetchMemberMap(UUID companyId) {
        try {
            var res = memberFeignClient.getMembersByCompany(companyId);
            if (res == null || res.getData() == null) return Collections.emptyMap();
            return res.getData().stream()
                    .collect(Collectors.toMap(
                            MemberResDto::getMemberId, m -> m, (a, b) -> a));
        } catch (Exception e) {
            log.warn("[SALARY-LIST] 직원 조회 실패 companyId={} 빈 Map 반환", companyId, e);
            return Collections.emptyMap();
        }
    }

    // 직원별 급여 이력 조회
    @Transactional(readOnly = true)
    public List<SalaryResDto> findByMemberId(UUID companyId, UUID memberId){
        return salaryRepository.findByMemberIdAndCompanyId(memberId, companyId).stream()
                .map(SalaryResDto::fromEntity).toList();
    }

    // 급여 수정
    public SalaryResDto update(UUID companyId, UUID salaryId, SalaryUpdateReqDto reqDto){
        Salary salary = salaryRepository.findById(salaryId)
                .orElseThrow(()-> new BusinessException(HttpStatus.NOT_FOUND, "급여 정보를 찾을수 없습니다."));

        if (!salary.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        // 급여 정책 존재 여부 확인
        if(!salaryPolicyRepository.existsById(reqDto.getSalaryPolicyId())){
            throw new BusinessException(HttpStatus.NOT_FOUND, "급여 정책을 찾을 수 없습니다.");
        }

        // 적용 기간 검증
        validateDateRange(reqDto.getEffectiveFrom(), reqDto.getEffectiveTo());

        SalaryPolicy policy = salaryPolicyRepository.findById(reqDto.getSalaryPolicyId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "급여 정책을 찾을 수 없습니다."));

        if ("Y".equals(policy.getUsePayGradeYn()) && reqDto.getStep() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "호봉제 정책입니다. 호봉을 지정하세요.");
        }

        long resolvedBaseSalary = resolveBaseSalary(
                companyId, reqDto.getStep(), reqDto.getBaseSalary(), reqDto.getEffectiveFrom());
        validateMinimumWage(resolvedBaseSalary, reqDto.getEffectiveFrom());

        salary.update(reqDto, resolvedBaseSalary);

        // 적용 종료일이 새로 설정/변경되었으면 해당 직원의 활성 수당 effectiveTo 도 동기화 (cap).
        // null -> 종료일 지정 시: 진행중 수당이 같은 날 종료
        // 더 늦게 끝나던 수당: 종료일에 맞춰 잘라냄 (이미 더 일찍 끝나는 행은 그대로)
        if (reqDto.getEffectiveTo() != null && salary.getMemberId() != null) {
            int touched = memberAllowanceRepository.syncEffectiveToByMember(
                    salary.getMemberId(), companyId, reqDto.getEffectiveTo());
            if (touched > 0) {
                log.info("[SALARY-UPDATE-CASCADE] memberId={} salaryId={} effectiveTo={} 로 수당 {}건 동기화",
                        salary.getMemberId(), salaryId, reqDto.getEffectiveTo(), touched);
            }
        }

        return SalaryResDto.fromEntity(salary);
    }

    // 급여 삭제 , 기본은 현재 적용 중인 급여 삭제 불가
    // force=true 시 활성 검사 우회 (관리자가 잘못 등록한 행을 즉시 정리할 때)
    // 삭제 후 해당 직원에게 남은 Salary 행이 없으면 MemberAllowance 도 함께 cascade 소프트 삭제 (orphan 정리)
    public void delete(UUID companyId, UUID salaryId, boolean force){
        Salary salary = salaryRepository.findById(salaryId)
                .orElseThrow(()-> new BusinessException(HttpStatus.NOT_FOUND, "급여 정보를 찾을 수 없습니다."));

        if (!salary.getCompanyId().equals(companyId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        if(!force && salary.isActive()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,"현재 적용 중인 급여는 삭제 불가합니다.");
        }

        UUID memberId = salary.getMemberId();
        salaryRepository.delete(salary);
        salaryRepository.flush(); // 이후 count 쿼리에 반영되도록 강제 flush

        // 직원의 Salary 가 모두 사라졌으면 - 정산 대상에서 빠지므로 남은 수당도 의미 없음 -> 일괄 소프트 삭제
        if (memberId != null) {
            List<Salary> remaining = salaryRepository.findByMemberIdAndCompanyId(memberId, companyId);
            if (remaining.isEmpty()) {
                int closed = memberAllowanceRepository.softDeleteAllByMember(memberId, companyId);
                if (closed > 0) {
                    log.info("[SALARY-DELETE-CASCADE] memberId={} companyId={} 잔여 Salary 0건 -> "
                                    + "MemberAllowance {} 건 소프트 삭제",
                            memberId, companyId, closed);
                }
            }
        }
    }

    // force 미지정 시 false
    public void delete(UUID companyId, UUID salaryId){
        delete(companyId, salaryId, false);
    }

    // 적용 종료일이 시작일보다 빠른지 검증
    private void validateDateRange(LocalDate effectiveFrom, LocalDate effectiveTo){
        if(effectiveTo != null && effectiveTo.isBefore(effectiveFrom)){
            throw new BusinessException(HttpStatus.BAD_REQUEST, "적용 종료일은 시작일보다 빠를 수 없습니다.");
        }
    }

    /**
     * 호봉이 지정되면 호봉표에서 baseSalary 조회, 없으면 reqDto 값 사용
     */
    private long resolveBaseSalary(UUID companyId, Integer step, Long fallbackBase,
                                   LocalDate effectiveFrom) {
        if (step != null) {
            PayGradeTable pg = payGradeTableRepository
                    .findActiveOn(companyId, step, effectiveFrom)
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                            step + "호봉이 " + effectiveFrom + " 기준 호봉표에 없습니다. "
                                    + "호봉표 먼저 등록하세요."));
            return pg.getBaseSalary();
        }
        if (fallbackBase == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "호봉을 지정하지 않으면 기본급을 직접 입력해야 합니다.");
        }
        return fallbackBase;
    }

    private void validateMinimumWage(long baseSalary, LocalDate effectiveFrom) {
        int year = effectiveFrom != null ? effectiveFrom.getYear() : LocalDate.now().getYear();
        Map.Entry<Integer, Long> floorEntry = MINIMUM_WAGE_HOURLY_BY_YEAR.floorEntry(year);
        long hourlyMin = floorEntry != null
                ? floorEntry.getValue()
                : MINIMUM_WAGE_HOURLY_BY_YEAR.firstEntry().getValue();
        long monthlyMin = hourlyMin * STANDARD_MONTHLY_WORK_HOURS;
        if (baseSalary < monthlyMin) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "기본급이 최저임금 기준 월 환산액("
                            + String.format("%,d", monthlyMin)
                            + "원)보다 낮습니다. 기준연도: "
                            + year
            );
        }
    }
}
