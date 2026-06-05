package com.sparta.one_stop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

// JPA Auditing 활성화 (BaseEntity의 @CreatedDate, @LastModifiedDate 동작)
// 스케줄러 활성화 (조회수 Redis → DB 동기화 등)
@EnableJpaAuditing
@EnableScheduling
@EnableRetry
@SpringBootApplication
public class OneStopApplication {

    public static void main(String[] args) {
        SpringApplication.run(OneStopApplication.class, args);
    }
}
