package com._team._team.attendance.repository;

import com._team._team.attendance.domain.LeaveRequest;
import com._team._team.attendance.domain.QLeaveRequest;
import com._team._team.attendance.domain.enums.LeaveApprovalStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class LeaveRequestRepositoryQuerydslImpl implements LeaveRequestRepositoryQuerydsl {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<LeaveRequest> searchLeaveRequests(
            UUID companyId, UUID memberId, LeaveApprovalStatus status, Pageable pageable) {

        QLeaveRequest l = QLeaveRequest.leaveRequest;
        BooleanBuilder where = baseWhere(companyId, memberId, status, l);

        // content 조회
        JPAQuery<LeaveRequest> contentQuery = queryFactory
                .selectFrom(l)
                .where(where)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize());
        applySort(contentQuery, pageable.getSort(), l);
        List<LeaveRequest> content = contentQuery.fetch();

        // count 쿼리
        Long total = queryFactory
                .select(l.count())
                .from(l)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public List<LeaveRequest> searchLeaveRequestsList(
            UUID companyId, UUID memberId, LeaveApprovalStatus status, Sort sort) {

        QLeaveRequest l = QLeaveRequest.leaveRequest;
        BooleanBuilder where = baseWhere(companyId, memberId, status, l);

        JPAQuery<LeaveRequest> query = queryFactory
                .selectFrom(l)
                .where(where);
        applySort(query, sort, l);
        return query.fetch();
    }

    /** companyId 필수 + delYn='N' 공통 조립 */
    private BooleanBuilder baseWhere(UUID companyId, UUID memberId,
                                     LeaveApprovalStatus status, QLeaveRequest l) {
        BooleanBuilder where = new BooleanBuilder()
                .and(l.companyId.eq(companyId))
                .and(l.delYn.eq("N"));

        BooleanExpression memberEq = memberId == null ? null : l.memberId.eq(memberId);
        BooleanExpression statusEq = status == null ? null : l.approvalStatus.eq(status);
        return where.and(memberEq).and(statusEq);
    }

    /**
     * - 화면별로 요청시간 DESC / ASC
     */
    private void applySort(JPAQuery<LeaveRequest> query, Sort sort, QLeaveRequest l) {
        if (sort == null || sort.isUnsorted()) {
            query.orderBy(l.requestedAt.desc());
            return;
        }
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        PathBuilder<LeaveRequest> pb = new PathBuilder<>(LeaveRequest.class, l.getMetadata());
        for (Sort.Order o : sort) {
            Order direction = o.isAscending() ? Order.ASC : Order.DESC;
            orders.add(new OrderSpecifier<>(direction,
                    Expressions.stringPath(pb, o.getProperty())));
        }
        query.orderBy(orders.toArray(new OrderSpecifier[0]));
    }
}
