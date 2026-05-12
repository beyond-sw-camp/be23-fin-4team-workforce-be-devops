package com._team._team;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.TimeZone;

@SpringBootApplication(scanBasePackages = "com._team._team")
@EnableJpaAuditing
@EnableAspectJAutoProxy
@EnableFeignClients
@EnableCaching
public class SalaryApplication {
	public static void main(String[] args) {
		// JVM TZ UTC 고정 - 모든 LocalDateTime.now() 가 UTC 시각 반환, 프론트 dayjs.utc().tz('Asia/Seoul') 와 짝
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(SalaryApplication.class, args);
	}
}
