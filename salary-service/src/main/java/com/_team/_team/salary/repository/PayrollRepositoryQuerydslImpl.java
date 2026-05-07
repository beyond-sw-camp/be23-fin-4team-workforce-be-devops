package com._team._team.salary.repository;

import com._team._team.salary.domain.Payroll;
import com._team._team.salary.domain.QPayroll;
import com._team._team.salary.domain.enums.PayrollStatus;
import com._team._team.salary.domain.enums.PayrollType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Payroll 동적 검색 구현 (QueryDSL)
 */
@Repository
@RequiredArgsConstructor
public class PayrollRepositoryQuerydslImpl implements PayrollRepositoryQuerydsl {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Payroll> searchAdminListInMonth(
            UUID companyId,
            LocalDate from,
            LocalDate to,
            PayrollStatus status,
            PayrollType payrollType,
            Collection<UUID> memberIds) {

        QPayroll p = QPayroll.payroll;

        // 비어있는 memberIds 가 들어오면 결과 없음 (in() 에 빈 컬렉션 넣으면 SQL 오류)
        if (memberIds != null && memberIds.isEmpty()) {
            return Collections.emptyList();
        }

        BooleanBuilder where = new BooleanBuilder()
                .and(p.companyId.eq(companyId))
                .and(p.delYn.eq("N"))
                .and(p.payrollYearMonthDay.between(from, to));

        BooleanExpression statusEq = status == null ? null : p.payrollStatus.eq(status);
        BooleanExpression typeEq = payrollType == null ? null : p.payrollType.eq(payrollType);
        BooleanExpression memberIn = (memberIds == null || memberIds.isEmpty())
                ? null
                : p.memberId.in(memberIds);

        where.and(statusEq).and(typeEq).and(memberIn);

        return queryFactory
                .selectFrom(p)
                .leftJoin(p.payrollItemList).fetchJoin()
                .where(where)
                .orderBy(p.payrollYearMonthDay.asc(), p.memberId.asc())
                .distinct()
                .fetch();
    }
}
