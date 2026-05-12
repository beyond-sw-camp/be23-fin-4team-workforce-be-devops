package com._team._team;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableFeignClients
@EnableRetry
@EnableScheduling
public class ApprovalApplication {

	public static void main(String[] args) {
		// JVM TZ UTC 고정 - 모든 LocalDateTime.now() 가 UTC 시각 반환, 프론트 dayjs.utc().tz('Asia/Seoul') 와 짝
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(ApprovalApplication.class, args);
	}

}
