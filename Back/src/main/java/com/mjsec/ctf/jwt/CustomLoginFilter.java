package com.mjsec.ctf.jwt;

import com.mjsec.ctf.domain.RefreshEntity;
import com.mjsec.ctf.dto.USER.UserDTO;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.repository.RefreshRepository;
import com.mjsec.ctf.repository.UserRepository;
import com.mjsec.ctf.service.JwtService;
import com.mjsec.ctf.type.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class CustomLoginFilter extends GenericFilterBean {

    private final UserRepository userRepository;
    private final RefreshRepository refreshRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper = new ObjectMapper(); // JSON 변환용

    public CustomLoginFilter(UserRepository userRepository, RefreshRepository refreshRepository,
                             JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshRepository = refreshRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {
        doFilter((HttpServletRequest) request, (HttpServletResponse) response, filterChain);
    }

    private void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {

        // 로그인 요청이 아닐 경우, 다음 필터로 넘김
        if (!request.getServletPath().equalsIgnoreCase("/api/users/sign-in") || !"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 요청 본문에서 JSON 데이터 추출
        UserDTO.SignIn loginRequest;
        try {
            loginRequest = objectMapper.readValue(request.getInputStream(), UserDTO.SignIn.class);
        } catch (IOException e) {
            log.error("Invalid login request format: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\": \"Invalid request format\"}");
            return;
        }

        // 아이디 검증 (아이디가 존재하지 않는 경우)
        var user = userRepository.findByLoginId(loginRequest.getLoginId()).orElse(null);

        if (user == null) {
            log.warn("Invalid login attempt with non-existing ID: {}", loginRequest.getLoginId());
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.INVALID_LOGIN_ID);
            return;
        }

        // 비밀번호 검증 (비밀번호가 틀린 경우)
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            log.warn("Invalid login attempt: Incorrect password for user: {}", loginRequest.getLoginId());
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.INVALID_PASSWORD);
            return;
        }

        // JWT 토큰 발급
        final long ACCESS_TOKEN_EXPIRY = 7200000L; // 2시간
        final long REFRESH_TOKEN_EXPIRY = 43200000L; // 12시간

        String accessToken = jwtService.createJwt("accessToken", user.getLoginId(), List.of(user.getRoles()), ACCESS_TOKEN_EXPIRY);
        String refreshToken = jwtService.createJwt("refreshToken", user.getLoginId(), List.of(user.getRoles()), REFRESH_TOKEN_EXPIRY);

        // Refresh Token 저장
        addRefreshEntity(user.getLoginId(),refreshToken,REFRESH_TOKEN_EXPIRY);

        // Refresh Token 쿠키 설정
        Cookie refreshCookie = new Cookie("refreshToken", refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(true);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge((int) REFRESH_TOKEN_EXPIRY / 1000);
        response.addCookie(refreshCookie);

        // Access Token을 헤더에 추가
        //response.setHeader("accessToken", accessToken);
        response.setHeader("Authorization", "Bearer " + accessToken);


        // 성공 응답 JSON 반환
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> message = new HashMap<>();
        message.put("message", "로그인 성공!");
        message.put("accessToken", accessToken);
        message.put("refreshToken", refreshToken);

        objectMapper.writeValue(response.getWriter(), message);
        response.setStatus(HttpServletResponse.SC_OK);

        log.info("User '{}' logged in successfully", user.getLoginId());
    }

    private void addRefreshEntity(String loginId, String refresh, Long expiredMs) {

        Date date = new Date(System.currentTimeMillis() + expiredMs);

        RefreshEntity refreshEntity = new RefreshEntity();
        refreshEntity.setLoginId(loginId);
        refreshEntity.setRefresh(refresh);
        refreshEntity.setExpiration(date.toString());

        refreshRepository.save(refreshEntity);
    }

    private void sendErrorResponse(HttpServletResponse response, int status, ErrorCode errorCode) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("code", errorCode.name());  // 예: "INVALID_LOGIN_ID"
        errorResponse.put("message", errorCode.getDescription());  // 예: "아이디를 잘못 입력하셨습니다."

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
