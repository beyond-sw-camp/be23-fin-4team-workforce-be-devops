package com._team._team.member.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// QueryDSL JPAQueryFactory 빈으로 등록
// 사원 검색 같은 동적쿼리에서 주입받아 씀
@Configuration
public class QuerydslConfig {

    @PersistenceContext
    private EntityManager entityManager;

    // 팩토리 하나만 만들어서 어디서든 주입해서 쓰기 위함
    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }
}
