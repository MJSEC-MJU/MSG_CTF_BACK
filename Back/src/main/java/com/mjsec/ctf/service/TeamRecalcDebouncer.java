package com.mjsec.ctf.service;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamRecalcDebouncer {

    private final RedissonClient redissonClient;
    private final TeamService teamService;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "team-recalc-debouncer");
        t.setDaemon(true);
        return t;
    });

    public void scheduleChallengeRecalc(Long challengeId) {
        String key = "recalc:challenge:" + challengeId;
        try {
            RBucket<String> bucket = redissonClient.getBucket(key);
            // 3초 TTL로 1회만 예약 (클러스터 전역 디바운스)
            boolean acquired = bucket.trySet("scheduled", 3, TimeUnit.SECONDS);
            if (!acquired) {
                return; // 이미 예약됨
            }

            // 2초 지연 후 재계산 수행
            scheduler.schedule(() -> {
                try {
                    teamService.recalculateTeamsByChallenge(challengeId);
                    log.info("[디바운스 재계산 완료] challengeId={}", challengeId);
                } catch (Exception e) {
                    log.warn("[디바운스 재계산 실패] challengeId={}, err={}", challengeId, e.getMessage(), e);
                }
            }, 2, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[디바운스 예약 실패] challengeId={}, err={}", challengeId, e.getMessage(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            scheduler.shutdown();
            scheduler.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}

