package com._team._team.attendance.repository;

import com._team._team.attendance.domain.QOvertimeRequest;
import com._team._team.attendance.domain.enums.OvertimeApprovalStatus;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OvertimeRequestRepositoryQuerydslImpl implements OvertimeRequestRepositoryQuerydsl {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<MemberApprovedMinutesRow> sumApprovedMinutesByCompanyAndRange(
            UUID companyId, LocalDate from, LocalDate to) {

        QOvertimeRequest r = QOvertimeRequest.overtimeRequest;

        List<Tuple> tuples = queryFactory
                .select(r.memberId, r.approvedMinutes.sum().coalesce(0))
                .from(r)
                .where(
                        r.companyId.eq(companyId),
                        r.approvalStatus.eq(OvertimeApprovalStatus.APPROVED),
                        r.targetDate.between(from, to)
                )
                .groupBy(r.memberId)
                .fetch();

        return tuples.stream()
                .map(t -> new MemberApprovedMinutesRow(
                        t.get(r.memberId),
                        t.get(r.approvedMinutes.sum().coalesce(0)).longValue()
                ))
                .toList();
    }
}
