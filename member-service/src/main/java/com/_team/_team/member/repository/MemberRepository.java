package com._team._team.member.repository;

import com._team._team.company.domain.Company;
import com._team._team.member.domain.Member;
import com._team._team.member.domain.enums.MemberStatus;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemberRepository extends JpaRepository<Member, UUID>, MemberRepositoryQuerydsl {
    // 이메일 중복 체크
    boolean existsByEmail(String email);

    // 이메일로 회원 조회 (로그인)
    Optional<Member> findByEmail(String email);

    // 개인 이메일 중복 체크
    boolean existsByPersonalEmail(String personalEmail);

    // 사번 + 회사 중복 체크
    boolean existsBySabunAndCompany(String sabun, Company company);

    // 비관적 락으로 마지막 사번 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m.sabun FROM Member m " +
            "WHERE m.company = :company " +
            "AND m.delYn = 'NO' " +
            "AND m.sabun LIKE 'EMP%' " +
            "ORDER BY m.sabun DESC")
    List<String> findLastSabunByCompanyWithLock(
            @Param("company") Company company);

    Optional<Member> findByPersonalEmail(String personalEmail);

    @Query("SELECT DISTINCT m FROM Member m " +
            "JOIN FETCH m.company " +
            "WHERE m.company = :company " +
            "AND m.delYn = :delYn")
    List<Member> findByCompanyAndDelYnWithPosition(
            @Param("company") Company company,
            @Param("delYn") String delYn);

    /**
     * 회사별 재직 사원 전체 조회 (배치용)
     * salary-service의 연차 부여 배치에서 Feign 호출로 사용
     * member-service "NO" / "YES"
     * salary-service"N" / "Y"
     */
    @Query("SELECT m FROM Member m " +
            "WHERE m.company.companyId = :companyId " +
            "AND m.memberStatus = :status " +
            "AND m.delYn = 'NO'")
    List<Member> findAllActiveByCompanyId(
            @Param("companyId") UUID companyId,
            @Param("status") MemberStatus status);
}
