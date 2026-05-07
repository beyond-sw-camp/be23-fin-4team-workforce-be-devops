package com._team._team.attendance.service;

import com._team._team.attendance.repository.LeavePolicyRepository;
import com._team._team.attendance.domain.LeavePolicy;
import com._team._team.batch.leave.worker.AnnualLeaveGrantWorker;
import com._team._team.attendance.domain.enums.AccrualBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 연차 부여 배치
 * - 회사별로 Worker 호출 (회사 단위 트랜잭션 격리는 Worker 담당)
 * - 전체 결과 집계 및 로깅
 */
@Service
public class AnnualLeaveGrantService {

    private final LeavePolicyRepository leavePolicyRepository;
    private final AnnualLeaveGrantWorker worker;

    @Autowired
    public AnnualLeaveGrantService(LeavePolicyRepository leavePolicyRepository, AnnualLeaveGrantWorker worker) {
        this.leavePolicyRepository = leavePolicyRepository;
        this.worker = worker;
    }

    // FISCAL : 회계연도 기준 일괄 부여 (1/1 스케줄) - FISCAL 정책 전체 회사 순회
    public BatchResult grantFiscal(LocalDate baseDate){

        List<LeavePolicy> policies = leavePolicyRepository.findAll().stream()
                .filter(p->"N".equals(p.getDelYn()))
                .filter(p->p.getAccrualBase() == AccrualBase.FISCAL)
                .toList();

        // 부여일은 1/1, 만료일은 12/31
        LocalDate grantDate = LocalDate.of(baseDate.getYear(), 1, 1);
        LocalDate expirationDate = LocalDate.of(baseDate.getYear(),  12, 31);

        BatchResult total = new BatchResult();
        for(LeavePolicy policy : policies){
            try {
                total.merge(worker.grantForCompanyFiscal(policy, grantDate, expirationDate));
            }catch (Exception e){
                total.errorCount++;
            }
        }
        return total;
    }

    // HIRE_DATE : 입사기념일 기준 (매일 스케줄) - HIRE_DATE 정책 전체 회사 순회
    public BatchResult grantHireDate(LocalDate baseDate){
        List<LeavePolicy> policies = leavePolicyRepository.findAll().stream()
                .filter(p->"N".equals(p.getDelYn()))
                .filter(p->p.getAccrualBase() == AccrualBase.HIRE_DATE)
                .toList();

        BatchResult total = new BatchResult();
        for(LeavePolicy policy : policies){
            try {
                total.merge(worker.grantForCompanyHireDate(policy, baseDate));
            } catch (Exception e) {
                total.errorCount++;
            }
        }
        return total;
    }

    // 단일 회사 수동 트리거 (컨트롤러에서 호출) - 회사 1건
    public BatchResult grantForCompanyManual(UUID companyId, LocalDate baseDate){
        LeavePolicy policy = leavePolicyRepository.findByCompanyIdAndDelYn(companyId, "N")
                .stream().findFirst()
                .orElseThrow(()-> new IllegalStateException("휴가 정책 없음"));

        if(policy.getAccrualBase() == AccrualBase.FISCAL){
            LocalDate grantDate = LocalDate.of(baseDate.getYear(), 1, 1);
            LocalDate expirationDate = LocalDate.of(baseDate.getYear(), 12, 31);
            return worker.grantForCompanyFiscal(policy, grantDate, expirationDate);
        }else{
            return worker.grantForCompanyHireDate(policy, baseDate);
        }
    }

    // 결과 집계 DTO (관리자의 확인용)
    public static class BatchResult {
        public int successCount;
        public int skipCount;
        public int errorCount;

        // 💡 실패한 사람의 정보나 에러 메시지를 담는 리스트
        public List<String> errorMessages = new ArrayList<>();

        public void merge(BatchResult other) {
            this.successCount += other.successCount;
            this.skipCount += other.skipCount;
            this.errorCount += other.errorCount;
            this.errorMessages.addAll(other.errorMessages);
        }
    }
}
