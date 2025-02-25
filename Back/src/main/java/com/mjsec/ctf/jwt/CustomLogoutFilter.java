package com.mjsec.ctf.jwt;

import com.mjsec.ctf.domain.BlacklistedTokenEntity;
import com.mjsec.ctf.domain.BlacklistedTokenEntity;
import com.mjsec.ctf.repository.BlacklistedTokenRepository;
import com.mjsec.ctf.repository.RefreshRepository;
import com.mjsec.ctf.service.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.GenericFilterBean;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class CustomLogoutFilter extends GenericFilterBean {
    private final JwtService jwtService;
    private final RefreshRepository refreshRepository;
    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final ObjectMapper objectMapper = new ObjectMapper(); // JSON 변환용

    public CustomLogoutFilter(JwtService jwtService, RefreshRepository refreshRepository,BlacklistedTokenRepository blacklistedTokenRepository) {
        this.jwtService = jwtService;
        this.refreshRepository = refreshRepository;
        this.blacklistedTokenRepository = blacklistedTokenRepository;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        doFilter((HttpServletRequest) request, (HttpServletResponse) response, filterChain);
    }

    private void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        // 로그아웃 요청이 아닌 경우, 다음 필터로 넘김
        if (!request.getRequestURI().equals("/api/users/logout") || !"POST".equals(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorizationHeader = request.getHeader("Authorization");
        if(authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("error: Missing or invalid Authorization header");
            return;
        }

        String accessToken = authorizationHeader.substring(7);

        // 토큰 만료 여부 확인
        try {
            jwtService.isExpired(accessToken);
        } catch (ExpiredJwtException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\": \"Access token has expired\"}");
            return;
        }

        // 블랙리스트에 토큰 추가
        BlacklistedTokenEntity blacklistedToken = new BlacklistedTokenEntity();
        blacklistedToken.setToken(accessToken);
        blacklistedToken.setExpiration(jwtService.getExpirationDate(accessToken));
        Date expirationDate = jwtService.getExpirationDate(accessToken);
        log.info("Blacklisting Token: {} | Expiration Time: {}", accessToken, expirationDate);
        blacklistedTokenRepository.save(blacklistedToken);

        log.info("Access token added to blacklist: {}", accessToken);

        // Refresh Token 가져오기
        String refresh = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refreshToken".equals(cookie.getName())) {
                    refresh = cookie.getValue();
                    break;
                }
            }
        }

        // Refresh Token이 없으면 오류 반환
        if (refresh == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\": \"Refresh token is missing\"}");
            return;
        }

        // Refresh Token 만료 체크
        try {
            jwtService.isExpired(refresh);
        } catch (ExpiredJwtException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\": \"Refresh token has expired\"}");
            return;
        }

        // Refresh Token인지 확인
        String tokenType = jwtService.getTokenType(refresh);
        if (!"refreshToken".equals(tokenType)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\": \"Invalid refresh token\"}");
            return;
        }

        // DB에서 Refresh Token이 존재하는지 확인
        if (!refreshRepository.existsByRefresh(refresh)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\": \"Refresh token not found in DB\"}");
            return;
        }

        // Refresh Token DB에서 삭제
        refreshRepository.deleteByRefresh(refresh);

        // Refresh Token 쿠키 제거
        Cookie cookie = new Cookie("refreshToken", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        //로그아웃 성공 메시지 반환
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> responseMessage = new HashMap<>();
        responseMessage.put("message", "로그아웃 성공!");

        objectMapper.writeValue(response.getWriter(), responseMessage);
    }
}
