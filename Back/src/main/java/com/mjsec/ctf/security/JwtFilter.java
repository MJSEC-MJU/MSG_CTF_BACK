package com.mjsec.ctf.security;

import com.mjsec.ctf.dto.USER.UserDTO;
import com.mjsec.ctf.service.JwtService;
import com.mjsec.ctf.type.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
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

    public JwtFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        log.info("Starting JWTFilter for request: {}", request.getRequestURI());

        /* 로그아웃 로직 개발중 잠시 주석처리
        String accessToken = request.getHeader("access");

        if (accessToken == null) {
            log.info("Access token is null, proceeding without authentication.");
            filterChain.doFilter(request, response);
            log.info("Completed JWTFilter for request: {}", request.getRequestURI());
            return;
        }

        if(jwtService.isExpired(accessToken)) {
            PrintWriter writer = response.getWriter();
            writer.print("access token expired");

            log.info("Access token is expired, proceeding without authentication.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String tokenType = jwtService.getTokenType(accessToken);

        if (!tokenType.equals("access")) {
            PrintWriter writer = response.getWriter();
            writer.print("invalid access token");

            log.info("Invalid access token, proceeding without authentication.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
         */
        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            log.info("Authorization header is null or invalid, proceeding without authentication.");
            filterChain.doFilter(request, response);
            log.info("Completed JWTFilter for request: {}", request.getRequestURI());
            return;
        }

        String accessToken = authorizationHeader.substring(7); // "Bearer " 이후 토큰 추출

        if (jwtService.isExpired(accessToken)) {
            log.info("Access token is expired, rejecting the request.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Access token expired");
            return;
        }

        String tokenType = jwtService.getTokenType(accessToken);
        if (!"access".equals(tokenType)) {
            log.info("Invalid token type, rejecting the request.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid access token");
            return;
        }

        // 기존에는 유저의 이메일과 권한을 가져왔지만 loginId를 가져오도록 커스터마이징 가능
        // -> loginId와 권한을 가져오도록 일단 설정

        String loginId = jwtService.getLoginId(accessToken);
        List<String> roles = jwtService.getRoles(accessToken);

        log.info("Token validated. loginId: {}, Roles: {}", loginId, roles);

        /*
        UserDTO.SignUp userDTO = new UserDTO.SignUp();
        userDTO.setLoginId(loginId);
        userDTO.setRoles(roles);

        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new) //String을 SimpleGrantedAuthority로 변환
                .collect(Collectors.toList());

        Authentication authToken = new UsernamePasswordAuthenticationToken(userDTO, null, authorities);
        */

        // 권한 문자열을 리스트로 변환
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        Authentication authToken = new UsernamePasswordAuthenticationToken(loginId, null, authorities);

        SecurityContextHolder.getContext().setAuthentication(authToken);
        log.info("User authenticated and set in SecurityContext: {}", loginId);

        filterChain.doFilter(request, response);
        log.info("Completed JWTFilter for request: {}", request.getRequestURI());
    }
}