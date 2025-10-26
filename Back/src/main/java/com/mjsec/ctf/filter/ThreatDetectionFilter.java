package com.mjsec.ctf.filter;

import com.mjsec.ctf.service.ThreatDetectionService;
import com.mjsec.ctf.util.IPAddressUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

/**
 * 공격 패턴 감지 필터
 * - Rate Limiting
 * - SQL Injection / XSS 감지
 * - 스캐닝 감지
 */
@Slf4j
public class ThreatDetectionFilter implements Filter {

    private final ThreatDetectionService threatDetectionService;

    public ThreatDetectionFilter(ThreatDetectionService threatDetectionService) {
        this.threatDetectionService = threatDetectionService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientIP = IPAddressUtil.getClientIP(httpRequest);
        String requestUri = httpRequest.getRequestURI();

        // 내부 네트워크 IP는 로그 제외 (localhost, Docker 내부 네트워크 등)
        if (IPAddressUtil.isLocalIP(clientIP)) {
            chain.doFilter(request, response);
            return;
        }

        // 사용자 정보 추출 (JWT 인증된 경우)
        Long userId = null;
        String loginId = null;
        boolean isAdmin = false;
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !authentication.getPrincipal().equals("anonymousUser")) {
                loginId = authentication.getName();
                // ADMIN 권한 확인
                isAdmin = authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(role -> role.equals("ROLE_ADMIN"));
            }
        } catch (Exception e) {
            // 인증 정보 없음 (익명 사용자)
        }

        // 1. Rate Limiting 체크 (사용자 정보 포함)
        boolean rateLimitPassed = threatDetectionService.checkRateLimit(clientIP, requestUri, userId, loginId);
        if (!rateLimitPassed) {
            log.warn("Rate limit exceeded for IP: {} | User: {} | URI: {}", clientIP, loginId != null ? loginId : "Anonymous", requestUri);
            httpResponse.setStatus(429); // 429 Too Many Requests
            httpResponse.setContentType("application/json");
            httpResponse.setCharacterEncoding("UTF-8");
            httpResponse.getWriter().write("{\"errorCode\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"너무 많은 요청이 감지되어 일시적으로 차단되었습니다.\"}");
            return;
        }

        // 2. SQL Injection / XSS 페이로드 감지 (ADMIN도 탐지되지만 차단은 안됨)
        boolean isSuspicious = threatDetectionService.detectSuspiciousPayload(clientIP, httpRequest, userId, loginId, isAdmin);
        if (isSuspicious && !isAdmin) {
            log.warn("Suspicious payload detected from IP: {} | User: {} | URI: {}", clientIP, loginId != null ? loginId : "Anonymous", requestUri);
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.setContentType("application/json");
            httpResponse.setCharacterEncoding("UTF-8");
            httpResponse.getWriter().write("{\"errorCode\":\"SUSPICIOUS_ACTIVITY\",\"message\":\"의심스러운 활동이 감지되어 차단되었습니다.\"}");
            return;
        }

        // 정상 요청 처리
        chain.doFilter(request, response);
    }
}
