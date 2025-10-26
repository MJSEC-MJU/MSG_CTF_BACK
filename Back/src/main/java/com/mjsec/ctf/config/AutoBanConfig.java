package com.mjsec.ctf.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 자동 차단 규칙 설정
 */
@Configuration
@Getter
public class AutoBanConfig {

    // 플래그 브루트포스 차단 규칙
    @Value("${auto-ban.flag-brute.max-wrong-attempts:10}")
    private int flagBruteMaxAttempts;

    @Value("${auto-ban.flag-brute.time-window-minutes:5}")
    private int flagBruteTimeWindowMinutes;

    @Value("${auto-ban.flag-brute.ban-duration-minutes:30}")
    private int flagBruteBanDurationMinutes;

    // API Rate Limiting 차단 규칙
    @Value("${auto-ban.rate-limit.max-requests-per-second:50}")
    private int rateLimitMaxRequestsPerSecond;

    @Value("${auto-ban.rate-limit.ban-duration-minutes:15}")
    private int rateLimitBanDurationMinutes;

    // 로그인 브루트포스 차단 규칙
    @Value("${auto-ban.login-brute.max-failed-attempts:5}")
    private int loginBruteMaxAttempts;

    @Value("${auto-ban.login-brute.time-window-minutes:10}")
    private int loginBruteTimeWindowMinutes;

    @Value("${auto-ban.login-brute.ban-duration-minutes:60}")
    private int loginBruteBanDurationMinutes;

    // 의심스러운 페이로드 차단 규칙
    @Value("${auto-ban.suspicious-payload.max-attempts:3}")
    private int suspiciousPayloadMaxAttempts;

    @Value("${auto-ban.suspicious-payload.ban-duration-minutes:120}")
    private int suspiciousPayloadBanDurationMinutes;

    // 404 접근 차단 규칙
    @Value("${auto-ban.not-found.max-attempts:20}")
    private int notFoundMaxAttempts;

    @Value("${auto-ban.not-found.time-window-minutes:5}")
    private int notFoundTimeWindowMinutes;

    @Value("${auto-ban.not-found.ban-duration-minutes:30}")
    private int notFoundBanDurationMinutes;

    // 자동 차단 활성화 여부
    @Value("${auto-ban.enabled:true}")
    private boolean autoBanEnabled;
}
