package com.mjsec.ctf.service;

import com.mjsec.ctf.config.AutoBanConfig;
import com.mjsec.ctf.domain.IPActivityEntity;
import com.mjsec.ctf.domain.IPBanEntity;
import com.mjsec.ctf.repository.IPActivityRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 공격 패턴 감지 및 자동 차단 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThreatDetectionService {

    private final IPActivityRepository ipActivityRepository;
    private final IPBanService ipBanService;
    private final AutoBanConfig autoBanConfig;

    // ========================================
    // SQL Injection 탐지 패턴 (OWASP ModSecurity CRS 기반)
    // ========================================

    // 1. SQL 키워드 기반 공격 (UNION, SELECT 등)
    private static final Pattern SQL_KEYWORDS = Pattern.compile(
        "(?i)(union|select|insert|update|delete|drop|create|alter|exec|execute|declare|cast|convert)\\s+(.*\\s+)?(from|into|where|table|database|schema)",
        Pattern.CASE_INSENSITIVE
    );

    // 2. SQL 주석 및 우회 시도 (--, /*, #)
    private static final Pattern SQL_COMMENTS = Pattern.compile(
        "(?i)(--|#|/\\*|\\*/|;--)",
        Pattern.CASE_INSENSITIVE
    );

    // 3. SQL 논리 연산자 (OR/AND 기반 인젝션)
    private static final Pattern SQL_LOGIC = Pattern.compile(
        "(?i)('\\s*(or|and)\\s*'|\"\\s*(or|and)\\s*\"|'\\s*(or|and)\\s*\\d|\"\\s*(or|and)\\s*\\d|'\\s*=\\s*'|\"\\s*=\\s*\")",
        Pattern.CASE_INSENSITIVE
    );

    // 4. SQL 시간 지연 공격 (SLEEP, BENCHMARK)
    private static final Pattern SQL_TIME_ATTACK = Pattern.compile(
        "(?i)(sleep|benchmark|waitfor|pg_sleep)\\s*\\(",
        Pattern.CASE_INSENSITIVE
    );

    // 5. 데이터베이스 정보 탐색
    private static final Pattern SQL_DB_NAMES = Pattern.compile(
        "(?i)(information_schema|mysql\\.user|sysobjects|syscolumns|pg_catalog|sqlite_master)",
        Pattern.CASE_INSENSITIVE
    );

    // 6. 16진수 및 인코딩 우회
    private static final Pattern SQL_HEX_ENCODING = Pattern.compile(
        "(?i)(0x[0-9a-f]{2,}|char\\(|chr\\()",
        Pattern.CASE_INSENSITIVE
    );

    // ========================================
    // XSS 탐지 패턴 (OWASP ModSecurity CRS 기반)
    // ========================================

    // 1. 스크립트 태그
    private static final Pattern XSS_SCRIPT_TAG = Pattern.compile(
        "(?i)<script[^>]*>[\\s\\S]*?</script>|<script[^>]*>",
        Pattern.CASE_INSENSITIVE
    );

    // 2. 이벤트 핸들러 (onerror, onload 등)
    private static final Pattern XSS_EVENT_HANDLER = Pattern.compile(
        "(?i)[\\s\"'`;/0-9=\\x09\\x0A\\x0C\\x0D\\x20]+on(error|load|click|mouse|focus|blur|change|submit)[\\s]*=",
        Pattern.CASE_INSENSITIVE
    );

    // 3. JavaScript URI 스킴
    private static final Pattern XSS_JAVASCRIPT_URI = Pattern.compile(
        "(?i)javascript\\s*:|data\\s*:text/html|vbscript\\s*:",
        Pattern.CASE_INSENSITIVE
    );

    // 4. 위험한 HTML 태그
    private static final Pattern XSS_DANGEROUS_TAGS = Pattern.compile(
        "(?i)<(iframe|embed|object|applet|meta|link|style|img)[^>]*>",
        Pattern.CASE_INSENSITIVE
    );

    // 5. JavaScript 함수 호출
    private static final Pattern XSS_JS_FUNCTIONS = Pattern.compile(
        "(?i)(eval|alert|confirm|prompt|document\\.cookie|document\\.write|window\\.location)\\s*\\(",
        Pattern.CASE_INSENSITIVE
    );

    // 6. AngularJS/템플릿 인젝션
    private static final Pattern XSS_TEMPLATE_INJECTION = Pattern.compile(
        "\\{\\{.*?\\}\\}|\\[\\[.*?\\]\\]|\\$\\{.*?\\}",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * 플래그 오답 기록 및 브루트포스 감지
     */
    @Transactional
    public void recordFlagAttempt(String ipAddress, boolean isCorrect, Long challengeId, Long userId, String loginId) {
        if (isCorrect || !autoBanConfig.isAutoBanEnabled()) {
            return;
        }

        // 오답 활동 기록
        IPActivityEntity activity = new IPActivityEntity();
        activity.setIpAddress(ipAddress);
        activity.setActivityType(IPActivityEntity.ActivityType.FLAG_SUBMIT_WRONG);
        activity.setActivityTime(LocalDateTime.now());
        activity.setRequestUri("/api/challenges/" + challengeId + "/submit");
        activity.setDetails("Challenge ID: " + challengeId);
        activity.setUserId(userId);
        activity.setLoginId(loginId);
        ipActivityRepository.save(activity);

        // 브루트포스 감지
        LocalDateTime checkSince = LocalDateTime.now()
            .minusMinutes(autoBanConfig.getFlagBruteTimeWindowMinutes());

        long wrongAttempts = ipActivityRepository.countByIpAndTypeAndTimeSince(
            ipAddress,
            IPActivityEntity.ActivityType.FLAG_SUBMIT_WRONG,
            checkSince
        );

        if (wrongAttempts >= autoBanConfig.getFlagBruteMaxAttempts()) {
            autoBanIP(
                ipAddress,
                String.format("플래그 브루트포스 공격 감지 (%d회 오답, %d분 이내)",
                    wrongAttempts, autoBanConfig.getFlagBruteTimeWindowMinutes()),
                autoBanConfig.getFlagBruteBanDurationMinutes(),
                loginId
            );
        }
    }

    /**
     * 로그인 실패 기록 및 브루트포스 감지
     */
    @Transactional
    public void recordLoginFailure(String ipAddress, String attemptedLoginId) {
        if (!autoBanConfig.isAutoBanEnabled()) {
            return;
        }

        // 로그인 실패 활동 기록
        IPActivityEntity activity = new IPActivityEntity();
        activity.setIpAddress(ipAddress);
        activity.setActivityType(IPActivityEntity.ActivityType.LOGIN_FAILED);
        activity.setActivityTime(LocalDateTime.now());
        activity.setRequestUri("/api/users/sign-in");
        activity.setDetails("Attempted login ID: " + attemptedLoginId);
        ipActivityRepository.save(activity);

        // 브루트포스 감지
        LocalDateTime checkSince = LocalDateTime.now()
            .minusMinutes(autoBanConfig.getLoginBruteTimeWindowMinutes());

        long failedAttempts = ipActivityRepository.countByIpAndTypeAndTimeSince(
            ipAddress,
            IPActivityEntity.ActivityType.LOGIN_FAILED,
            checkSince
        );

        if (failedAttempts >= autoBanConfig.getLoginBruteMaxAttempts()) {
            autoBanIP(
                ipAddress,
                String.format("로그인 브루트포스 공격 감지 (%d회 실패, %d분 이내)",
                    failedAttempts, autoBanConfig.getLoginBruteTimeWindowMinutes()),
                autoBanConfig.getLoginBruteBanDurationMinutes(),
                null
            );
        }
    }

    /**
     * API Rate Limiting 체크
     */
    @Transactional
    public boolean checkRateLimit(String ipAddress, String requestUri) {
        if (!autoBanConfig.isAutoBanEnabled()) {
            return true; // 통과
        }

        // API 요청 활동 기록
        IPActivityEntity activity = new IPActivityEntity();
        activity.setIpAddress(ipAddress);
        activity.setActivityType(IPActivityEntity.ActivityType.API_REQUEST);
        activity.setActivityTime(LocalDateTime.now());
        activity.setRequestUri(requestUri);
        ipActivityRepository.save(activity);

        // 1초 내 요청 수 체크
        LocalDateTime oneSecondAgo = LocalDateTime.now().minusSeconds(1);

        long recentRequests = ipActivityRepository.countByIpAndTimeSince(ipAddress, oneSecondAgo);

        // Rate Limit 초과 시 경고만 로그에 남기고 차단은 하지 않음
        // (정상 사용자가 새로고침 등으로 많은 요청을 보낼 수 있음)
        if (recentRequests >= autoBanConfig.getRateLimitMaxRequestsPerSecond()) {
            log.warn("⚠️ Rate Limit Warning: IP {} | {} requests/sec | URI: {}",
                     ipAddress, recentRequests, requestUri);

            // 극단적인 경우만 차단 (1초에 200회 이상)
            if (recentRequests >= autoBanConfig.getRateLimitMaxRequestsPerSecond() * 2) {
                autoBanIP(
                    ipAddress,
                    String.format("극단적 Rate Limit 초과 (1초에 %d회 요청)", recentRequests),
                    autoBanConfig.getRateLimitBanDurationMinutes(),
                    null
                );
                return false; // 차단
            }
        }

        return true; // 통과
    }

    /**
     * 의심스러운 페이로드 감지 (OWASP ModSecurity CRS 기반)
     */
    @Transactional
    public boolean detectSuspiciousPayload(String ipAddress, HttpServletRequest request) {
        if (!autoBanConfig.isAutoBanEnabled()) {
            return false;
        }

        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();

        // 요청 파라미터 검사
        boolean isSuspicious = false;
        String suspiciousContent = null;
        String attackType = null;

        // Query String을 URL 디코딩하여 검사
        if (queryString != null) {
            try {
                // URL 디코딩 (공격자가 인코딩을 통해 우회하는 것 방지)
                queryString = URLDecoder.decode(queryString, StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("Failed to decode query string: {}", queryString);
            }

            // Query String 검사 (SQL Injection)
            if (SQL_KEYWORDS.matcher(queryString).find()) {
                isSuspicious = true;
                suspiciousContent = "Query: " + queryString;
                attackType = "SQL Injection - Keywords";
            } else if (SQL_COMMENTS.matcher(queryString).find()) {
                isSuspicious = true;
                suspiciousContent = "Query: " + queryString;
                attackType = "SQL Injection - Comments";
            } else if (SQL_LOGIC.matcher(queryString).find()) {
                isSuspicious = true;
                suspiciousContent = "Query: " + queryString;
                attackType = "SQL Injection - Logic";
            } else if (SQL_TIME_ATTACK.matcher(queryString).find()) {
                isSuspicious = true;
                suspiciousContent = "Query: " + queryString;
                attackType = "SQL Injection - Time Attack";
            } else if (SQL_DB_NAMES.matcher(queryString).find()) {
                isSuspicious = true;
                suspiciousContent = "Query: " + queryString;
                attackType = "SQL Injection - DB Names";
            } else if (SQL_HEX_ENCODING.matcher(queryString).find()) {
                isSuspicious = true;
                suspiciousContent = "Query: " + queryString;
                attackType = "SQL Injection - Hex Encoding";
            }

            // Query String 검사 (XSS)
            if (!isSuspicious) {
                if (XSS_SCRIPT_TAG.matcher(queryString).find()) {
                    isSuspicious = true;
                    suspiciousContent = "Query: " + queryString;
                    attackType = "XSS - Script Tag";
                } else if (XSS_EVENT_HANDLER.matcher(queryString).find()) {
                    isSuspicious = true;
                    suspiciousContent = "Query: " + queryString;
                    attackType = "XSS - Event Handler";
                } else if (XSS_JAVASCRIPT_URI.matcher(queryString).find()) {
                    isSuspicious = true;
                    suspiciousContent = "Query: " + queryString;
                    attackType = "XSS - JavaScript URI";
                } else if (XSS_DANGEROUS_TAGS.matcher(queryString).find()) {
                    isSuspicious = true;
                    suspiciousContent = "Query: " + queryString;
                    attackType = "XSS - Dangerous Tags";
                } else if (XSS_JS_FUNCTIONS.matcher(queryString).find()) {
                    isSuspicious = true;
                    suspiciousContent = "Query: " + queryString;
                    attackType = "XSS - JS Functions";
                } else if (XSS_TEMPLATE_INJECTION.matcher(queryString).find()) {
                    isSuspicious = true;
                    suspiciousContent = "Query: " + queryString;
                    attackType = "XSS - Template Injection";
                }
            }
        }

        // User-Agent 헤더 검사
        if (!isSuspicious) {
            String userAgent = request.getHeader("User-Agent");
            if (userAgent != null) {
                if (SQL_KEYWORDS.matcher(userAgent).find() ||
                    SQL_COMMENTS.matcher(userAgent).find() ||
                    XSS_SCRIPT_TAG.matcher(userAgent).find() ||
                    XSS_EVENT_HANDLER.matcher(userAgent).find()) {
                    isSuspicious = true;
                    suspiciousContent = "User-Agent: " + userAgent;
                    attackType = "Header Injection";
                }
            }
        }

        // Referer 헤더 검사
        if (!isSuspicious) {
            String referer = request.getHeader("Referer");
            if (referer != null) {
                if (XSS_JAVASCRIPT_URI.matcher(referer).find() ||
                    XSS_SCRIPT_TAG.matcher(referer).find()) {
                    isSuspicious = true;
                    suspiciousContent = "Referer: " + referer;
                    attackType = "Referer XSS";
                }
            }
        }

        if (isSuspicious) {
            // 의심스러운 활동 기록
            IPActivityEntity activity = new IPActivityEntity();
            activity.setIpAddress(ipAddress);
            activity.setActivityType(IPActivityEntity.ActivityType.SUSPICIOUS_PAYLOAD);
            activity.setActivityTime(LocalDateTime.now());
            activity.setRequestUri(requestUri);
            activity.setDetails(attackType + " | " + suspiciousContent);
            activity.setIsSuspicious(true);
            ipActivityRepository.save(activity);

            log.warn("🚨 Suspicious Payload Detected: IP {} | Type: {} | URI: {}",
                     ipAddress, attackType, requestUri);

            // 3회 이상 의심 활동 시 차단
            LocalDateTime checkSince = LocalDateTime.now().minusHours(1);
            long suspiciousCount = ipActivityRepository.countByIpAndTypeAndTimeSince(
                ipAddress,
                IPActivityEntity.ActivityType.SUSPICIOUS_PAYLOAD,
                checkSince
            );

            if (suspiciousCount >= autoBanConfig.getSuspiciousPayloadMaxAttempts()) {
                autoBanIP(
                    ipAddress,
                    String.format("%s 공격 시도 감지 (%d회)", attackType, suspiciousCount),
                    autoBanConfig.getSuspiciousPayloadBanDurationMinutes(),
                    null
                );
                return true; // 차단
            }
        }

        return false;
    }

    /**
     * 404 접근 기록 및 스캐닝 감지
     */
    @Transactional
    public void recordNotFoundAccess(String ipAddress, String requestUri) {
        if (!autoBanConfig.isAutoBanEnabled()) {
            return;
        }

        // 404 활동 기록
        IPActivityEntity activity = new IPActivityEntity();
        activity.setIpAddress(ipAddress);
        activity.setActivityType(IPActivityEntity.ActivityType.NOT_FOUND_ACCESS);
        activity.setActivityTime(LocalDateTime.now());
        activity.setRequestUri(requestUri);
        ipActivityRepository.save(activity);

        // 스캐닝 감지
        LocalDateTime checkSince = LocalDateTime.now()
            .minusMinutes(autoBanConfig.getNotFoundTimeWindowMinutes());

        long notFoundCount = ipActivityRepository.countByIpAndTypeAndTimeSince(
            ipAddress,
            IPActivityEntity.ActivityType.NOT_FOUND_ACCESS,
            checkSince
        );

        if (notFoundCount >= autoBanConfig.getNotFoundMaxAttempts()) {
            autoBanIP(
                ipAddress,
                String.format("디렉토리 스캐닝 시도 감지 (%d회 404, %d분 이내)",
                    notFoundCount, autoBanConfig.getNotFoundTimeWindowMinutes()),
                autoBanConfig.getNotFoundBanDurationMinutes(),
                null
            );
        }
    }

    /**
     * 자동 IP 차단
     */
    private void autoBanIP(String ipAddress, String reason, long durationMinutes, String detectedLoginId) {
        try {
            // 이미 차단된 IP인지 확인
            if (ipBanService.isBanned(ipAddress)) {
                log.debug("IP {} is already banned, skipping auto-ban", ipAddress);
                return;
            }

            ipBanService.banIP(
                ipAddress,
                "[자동 차단] " + reason,
                IPBanEntity.BanType.TEMPORARY,
                durationMinutes,
                null, // 자동 차단이므로 adminId null
                "AUTO_BAN_SYSTEM"
            );

            log.warn("🚨 Auto-banned IP: {} | Reason: {} | Duration: {} minutes | Detected User: {}",
                ipAddress, reason, durationMinutes, detectedLoginId != null ? detectedLoginId : "Unknown");
        } catch (Exception e) {
            log.error("Failed to auto-ban IP {}: {}", ipAddress, e.getMessage(), e);
        }
    }

    /**
     * 오래된 활동 기록 정리 (매일 자정 실행)
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanupOldActivities() {
        try {
            // 7일 이상 된 활동 기록 삭제
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            int deletedCount = ipActivityRepository.deleteOldActivities(sevenDaysAgo);

            log.info("Cleaned up {} old activity records (older than 7 days)", deletedCount);
        } catch (Exception e) {
            log.error("Failed to cleanup old activities: {}", e.getMessage(), e);
        }
    }
}
