package com.sparta.one_stop.global.audit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Slf4j @Configuration @EnableAsync
public class SecurityAuditAsyncConfig {
    @Bean(name="securityAuditExecutor")
    public Executor securityAuditExecutor(){
        ThreadPoolTaskExecutor e=new ThreadPoolTaskExecutor(); e.setCorePoolSize(2); e.setMaxPoolSize(4);
        e.setQueueCapacity(1000); e.setThreadNamePrefix("security-audit-");
        e.setRejectedExecutionHandler((r,x)->log.warn("[SECURITY_AUDIT_DROPPED] audit queue is full"));
        e.initialize(); return e;
    }
}
