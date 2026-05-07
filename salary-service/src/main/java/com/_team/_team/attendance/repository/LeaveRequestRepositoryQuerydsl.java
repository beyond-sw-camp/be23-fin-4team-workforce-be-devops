package com._team._team.attendance.repository;

import com._team._team.attendance.domain.LeaveRequest;
import com._team._team.attendance.domain.enums.LeaveApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

/**
 * 휴가 요청 동적 검색 (QueryDSL)
 * - 본인 이력 / 관리자 결재함 / 휴가 이력 모달, 여러 화면 공유
 */
public interface LeaveRequestRepositoryQuerydsl {

    /**
     * 회사 + 옵셔널 필터로 휴가요청 페이지 검색
     * - memberId null 이면 회사 전체 (관리자 모드)
     * - status  null 이면  -> 모든 상태
     */
    Page<LeaveRequest> searchLeaveRequests(
            UUID companyId,
            UUID memberId,
            LeaveApprovalStatus status,
            Pageable pageable);

    /**
     * 페이징 없이 list 가 필요한 케이스 (관리자 결재함 list)
     */
    List<LeaveRequest> searchLeaveRequestsList(
            UUID companyId,
            UUID memberId,
            LeaveApprovalStatus status,
            Sort sort);
}
