package com._team._team.evaluation.scheduler;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 활성화. goal-service 의 main Application 클래스에 @EnableScheduling 가 이미 있으면 본 파일 불필요.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
