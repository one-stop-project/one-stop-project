package com.sparta.one_stop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// JPA Auditing 활성화 (BaseEntity의 @CreatedDate, @LastModifiedDate 동작)
@EnableJpaAuditing
@SpringBootApplication
public class OneStopApplication {

    public static void main(String[] args) {
        SpringApplication.run(OneStopApplication.class, args);
    }
}
