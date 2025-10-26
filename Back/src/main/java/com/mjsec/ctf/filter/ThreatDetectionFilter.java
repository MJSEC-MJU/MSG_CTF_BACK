package com.mjsec.ctf.filter;

import com.mjsec.ctf.service.ThreatDetectionService;
import com.mjsec.ctf.util.IPAddressUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

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

        // 1. Rate Limiting 체크
        boolean rateLimitPassed = threatDetectionService.checkRateLimit(clientIP, requestUri);
        if (!rateLimitPassed) {
            log.warn("Rate limit exceeded for IP: {} | URI: {}", clientIP, requestUri);
            httpResponse.setStatus(429); // 429 Too Many Requests
            httpResponse.setContentType("application/json");
            httpResponse.setCharacterEncoding("UTF-8");
            httpResponse.getWriter().write("{\"errorCode\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"너무 많은 요청이 감지되어 일시적으로 차단되었습니다.\"}");
            return;
        }

        // 2. SQL Injection / XSS 페이로드 감지
        boolean isSuspicious = threatDetectionService.detectSuspiciousPayload(clientIP, httpRequest);
        if (isSuspicious) {
            log.warn("Suspicious payload detected from IP: {} | URI: {}", clientIP, requestUri);
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
