package com.mjsec.ctf.service;

import com.mjsec.ctf.repository.BlacklistedTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlacklistedTokenCleanupService {
    private final BlacklistedTokenRepository blacklistedTokenRepository;

    @Scheduled(fixedRate = 7200000)
    public void cleanupExpiredTokens(){
        Date now = new Date();
        log.info("cleaning up expired tokens at {}", now);

        int deletedCount = blacklistedTokenRepository.deleteExpiredTokens(now);
        log.info("✅ {} expired tokens deleted", deletedCount);
    }
}
