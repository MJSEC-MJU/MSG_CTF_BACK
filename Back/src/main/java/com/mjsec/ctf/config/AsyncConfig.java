package com.mjsec.ctf.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "submissionAsyncExecutor")
    public Executor submissionAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 기본 스레드 풀 크기: 10개
        // 평소에는 10개 스레드로 충분히 처리 가능
        executor.setCorePoolSize(10);

        // 최대 스레드 풀 크기: 50개
        // 동시 제출이 많을 때 최대 50개까지 스레드 생성
        executor.setMaxPoolSize(50);

        // 대기 큐 크기: 200개
        // 모든 스레드가 바쁠 때 200개까지 대기 가능
        executor.setQueueCapacity(200);

        // 유휴 스레드 유지 시간: 60초
        // CorePoolSize를 초과하는 스레드는 60초 동안 작업이 없으면 종료
        executor.setKeepAliveSeconds(60);

        // 스레드 이름 접두사
        executor.setThreadNamePrefix("submission-async-");

        // 거부 정책: CallerRunsPolicy
        // 큐가 가득 차고 최대 스레드 수에 도달하면, 호출한 스레드에서 직접 실행
        // 이렇게 하면 요청을 버리지 않고 처리할 수 있음 (속도는 느려지지만 안정적)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 애플리케이션 종료 시 모든 작업이 완료될 때까지 대기
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // 종료 대기 시간: 60초
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}
