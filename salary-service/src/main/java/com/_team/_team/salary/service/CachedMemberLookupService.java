package com._team._team.salary.service;

import com._team._team.dto.ApiResponse;
import com._team._team.salary.feignClients.MemberFeignClient;
import com._team._team.salary.feignClients.dto.MemberResDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * member-service Feign 호출 캐싱 래퍼
 * - 같은 회사 직원 list 를 여러 서비스에서 빈번하게 조회 (PayrollService / SalaryService /
 *   PayrollExportService / RegularBonusPaymentWorker / OvertimeUsageService 등 9곳+)
 * - Redis 캐시 5분 TTL (CacheConfig 의 defaults)
 * - 직원 입사/퇴사 등 변동 시 evict 호출하여 stale 방지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CachedMemberLookupService {

    private final MemberFeignClient memberFeignClient;

    /**
     * 회사 소속 재직 직원 list (캐시 일시 비활성)
     */
    public List<MemberResDto> getMembersByCompany(UUID companyId) {
        try {
            ApiResponse<List<MemberResDto>> resp = memberFeignClient.getMembersByCompany(companyId);
            return resp != null && resp.getData() != null
                    ? resp.getData()
                    : Collections.emptyList();
        } catch (Exception e) {
            log.warn("[CachedMemberLookup] member-service 조회 실패 companyId={} - {}",
                    companyId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 캐시 무효화 - 직원 입사/퇴사/조직 변경 등 알림 받을 때 호출
     */
    @CacheEvict(value = "membersByCompany", key = "#companyId")
    public void evict(UUID companyId) {
        log.info("[CachedMemberLookup] 캐시 evict companyId={}", companyId);
    }
}
