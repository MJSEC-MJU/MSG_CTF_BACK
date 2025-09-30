package com.mjsec.ctf.filter;

import com.mjsec.ctf.repository.BlacklistedTokenRepository;
import com.mjsec.ctf.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final BlacklistedTokenRepository blacklistedTokenRepository;

    public JwtFilter(JwtService jwtService, BlacklistedTokenRepository blacklistedTokenRepository) {
        this.jwtService = jwtService;
        this.blacklistedTokenRepository = blacklistedTokenRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        log.info("Starting JWTFilter for request: {}", request.getRequestURI());

        // JWT 검증을 건너뛸 public 엔드포인트 설정
        if (request.getRequestURI().equals("/api/users/sign-in")|| request.getRequestURI().equals("/api/users/sign-up") ||
                request.getRequestURI().equals("/api/leaderboard") ||
                request.getRequestURI().equals("/api/leaderboard/graph") ||
                request.getRequestURI().equals("/api/leaderboard/stream")) {
            log.info("Skipping JWT filter for public endpoint: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            log.info("Authorization header is null or invalid, proceeding without authentication.");
            filterChain.doFilter(request, response);
            log.info("Completed JWTFilter for request: {}", request.getRequestURI());
            return;
        }

        String accessToken = authorizationHeader.substring(7); // "Bearer " 이후 토큰 추출

        if (blacklistedTokenRepository.existsByToken(accessToken)) {
            log.warn("Access token is blacklisted: {}", accessToken);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Access token is blacklisted");
            return;
        }

        // 토큰 만료 확인 및 재발급 시도
        if (jwtService.isExpired(accessToken)) {
            log.info("Access token is expired, rejecting the request.");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Access token expired");
            return;
        }

        // 토큰 타입 검증
        String tokenType = jwtService.getTokenType(accessToken);
        if (!"accessToken".equals(tokenType)) {
            log.info("Invalid token type, rejecting the request.");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid access token");
            return;
        }

        // 사용자 정보 추출 및 SecurityContext에 설정
        String loginId = jwtService.getLoginId(accessToken);
        List<String> role = jwtService.getRole(accessToken);

        log.info("Token validated. loginId: {}, Role: {}", loginId, role);

        List<SimpleGrantedAuthority> authorities = role.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        Authentication authToken = new UsernamePasswordAuthenticationToken(loginId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authToken);
        log.info("User authenticated and set in SecurityContext: {}", loginId);

        filterChain.doFilter(request, response);
        log.info("Completed JWTFilter for request: {}", request.getRequestURI());
    }
}