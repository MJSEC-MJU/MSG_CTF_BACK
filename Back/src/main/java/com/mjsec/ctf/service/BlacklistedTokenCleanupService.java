package com.mjsec.ctf.service;

import com.mjsec.ctf.repository.BlacklistedTokenRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class BlacklistedTokenCleanupService {
    private final BlacklistedTokenRepository blacklistedTokenRepository;

    @Scheduled(fixedRate = 1800000)
    public void cleanupExpiredTokens() {
        try {
            Date now = new Date();
            log.info("Scheduled Task: cleaning up expired tokens at {}", now);

            int deletedCount = blacklistedTokenRepository.deleteExpiredTokens(now);
            log.info("{} expired tokens deleted", deletedCount);
        } catch (Exception e) {
            log.error("Error during cleanupExpiredTokens: ", e);
        }
    }

}
