package com._team._team.member.repository;

import com._team._team.member.domain.QMember;
import com._team._team.member.domain.QMemberPosition;
import com._team._team.member.domain.enums.EmploymentType;
import com._team._team.member.domain.enums.MemberStatus;
import com._team._team.member.dto.reqdto.MemberSearchCondition;
import com._team._team.member.dto.resdto.MemberSearchItemResDto;
import com._team._team.member.dto.resdto.QMemberSearchItemResDto;
import com._team._team.organization.domain.QJobGrade;
import com._team._team.organization.domain.QJobTitle;
import com._team._team.organization.domain.QOrganization;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 사원 검색 QueryDSL 구현체
@RequiredArgsConstructor
public class MemberRepositoryQuerydslImpl implements MemberRepositoryQuerydsl {

    private final JPAQueryFactory queryFactory;

    private static final QMember m = QMember.member;
    private static final QMemberPosition mp = QMemberPosition.memberPosition;
    private static final QOrganization org = QOrganization.organization;
    private static final QJobTitle jt = QJobTitle.jobTitle;
    private static final QJobGrade jg = QJobGrade.jobGrade;

    @Override
    public Page<MemberSearchItemResDto> searchMembers(MemberSearchCondition cond, Pageable pageable) {

        // 조건 묶기
        BooleanBuilder where = buildWhere(cond);

        // 메인 쿼리: 활성 포지션 left join, 같은 사원 중복 방지 위해 distinct
        List<MemberSearchItemResDto> content = queryFactory
                .select(new QMemberSearchItemResDto(
                        m.memberId,
                        m.name,
                        m.sabun,
                        m.email,
                        m.profileUrl,
                        m.joinDate,
                        m.memberStatus,
                        m.employmentType,
                        org.organizationId,
                        org.name,
                        jt.jobTitleId,
                        jt.name,
                        jg.jobGradeId,
                        jg.name
                ))
                .from(m)
                .leftJoin(mp).on(mp.member.eq(m)
                        .and(mp.isActiveYn.eq("YES"))
                        .and(mp.delYn.eq("NO")))
                .leftJoin(mp.organization, org)
                .leftJoin(mp.jobTitle, jt)
                .leftJoin(mp.jobGrade, jg)
                .where(where)
                .orderBy(toOrderSpecifiers(pageable.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // count 쿼리
        Long totalBoxed = queryFactory
                .select(m.count())
                .from(m)
                .where(where)
                .fetchOne();
        long total = totalBoxed == null ? 0L : totalBoxed;
        return new PageImpl<>(content, pageable, total);
    }

    // 조건 조립 (null이면 무시)
    private BooleanBuilder buildWhere(MemberSearchCondition cond) {
        BooleanBuilder b = new BooleanBuilder();

        // 삭제 포함 안 하면 delYn = 'NO'만
        if (!cond.isIncludeDeleted()) {
            b.and(m.delYn.eq("NO"));
        }
        b.and(eqCompany(cond.getCompanyId()));
        b.and(keywordContains(cond.getKeyword()));
        b.and(eqMemberStatus(cond.getMemberStatus()));
        b.and(eqEmploymentType(cond.getEmploymentType()));
        b.and(joinDateBetween(cond.getJoinDateFrom(), cond.getJoinDateTo()));

        // 조직/직책/직급은 활성 포지션 기준
        if (cond.getOrganizationId() != null
                || cond.getJobTitleId() != null
                || cond.getJobGradeId() != null) {
            b.and(existsActivePositionMatch(cond));
        }
        return b;
    }

    private BooleanExpression eqCompany(UUID companyId) {
        return companyId != null ? m.company.companyId.eq(companyId) : null;
    }

    // 이름/사번/이메일 부분일치 OR
    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) return null;
        String trimmed = keyword.trim();
        return m.name.containsIgnoreCase(trimmed)
                .or(m.sabun.containsIgnoreCase(trimmed))
                .or(m.email.containsIgnoreCase(trimmed));
    }

    private BooleanExpression eqMemberStatus(MemberStatus s) {
        return s != null ? m.memberStatus.eq(s) : null;
    }

    private BooleanExpression eqEmploymentType(EmploymentType e) {
        return e != null ? m.employmentType.eq(e) : null;
    }

    private BooleanExpression joinDateBetween(LocalDate from, LocalDate to) {
        if (from == null && to == null) return null;
        if (from != null && to != null) return m.joinDate.between(from, to);
        if (from != null) return m.joinDate.goe(from);
        return m.joinDate.loe(to);
    }

    // 활성 포지션 중 조건 일치하는게 있는지 EXISTS
    private BooleanExpression existsActivePositionMatch(MemberSearchCondition cond) {
        QMemberPosition sub = new QMemberPosition("subMp");
        BooleanBuilder subWhere = new BooleanBuilder();
        subWhere.and(sub.member.eq(m));
        subWhere.and(sub.isActiveYn.eq("YES"));
        subWhere.and(sub.delYn.eq("NO"));
        if (cond.getOrganizationId() != null) {
            subWhere.and(sub.organization.organizationId.eq(cond.getOrganizationId()));
        }
        if (cond.getJobTitleId() != null) {
            subWhere.and(sub.jobTitle.jobTitleId.eq(cond.getJobTitleId()));
        }
        if (cond.getJobGradeId() != null) {
            subWhere.and(sub.jobGrade.jobGradeId.eq(cond.getJobGradeId()));
        }
        return com.querydsl.jpa.JPAExpressions.selectOne().from(sub).where(subWhere).exists();
    }

    // pageable 정렬
    private OrderSpecifier<?>[] toOrderSpecifiers(Sort sort) {
        if (sort == null || sort.isEmpty()) {
            return new OrderSpecifier[]{ m.name.asc() };
        }
        PathBuilder<Object> path = new PathBuilder<>(Object.class, "member");
        return sort.stream()
                .filter(o -> isAllowedSortField(o.getProperty()))
                .map(o -> {
                    com.querydsl.core.types.Order direction = o.isAscending()
                            ? com.querydsl.core.types.Order.ASC
                            : com.querydsl.core.types.Order.DESC;
                    return switch (o.getProperty()) {
                        case "name" -> new OrderSpecifier<>(direction, m.name);
                        case "sabun" -> new OrderSpecifier<>(direction, m.sabun);
                        case "joinDate" -> new OrderSpecifier<>(direction, m.joinDate);
                        case "email" -> new OrderSpecifier<>(direction, m.email);
                        default -> new OrderSpecifier<>(direction, m.name);
                    };
                })
                .toArray(OrderSpecifier[]::new);
    }

    private boolean isAllowedSortField(String field) {
        return field.equals("name") || field.equals("sabun")
                || field.equals("joinDate") || field.equals("email");
    }
}
