package com._team._team;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * [SSE] 알림 파이프라인이 정상 동작하려면 다음이 필요:
 *   - 이벤트 리스너는 {@code common.NotificationEventListener} 가 담당
 *   - 해당 리스너는 {@code @TransactionalEventListener(AFTER_COMMIT) + @Async} 로 동작
 *   - {@code @EnableAsync} 는 common.AsyncConfig 에도 있지만, 모듈 간 스캔/순서 이슈 방어를 위해 여기서도 명시
 */
@SpringBootApplication(scanBasePackages = "com._team._team")
@EnableJpaAuditing
@EnableAspectJAutoProxy
@EnableScheduling
@EnableAsync
@EnableFeignClients
public class GoalApplication {

	public static void main(String[] args) {
		SpringApplication.run(GoalApplication.class, args);
	}

}