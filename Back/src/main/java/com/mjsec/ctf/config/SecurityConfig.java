package com.mjsec.ctf.config;

import com.mjsec.ctf.filter.CustomLoginFilter;
import com.mjsec.ctf.filter.CustomLogoutFilter;
import com.mjsec.ctf.filter.IPBanFilter;
import com.mjsec.ctf.filter.ThreatDetectionFilter;
import com.mjsec.ctf.repository.BlacklistedTokenRepository;
import com.mjsec.ctf.repository.RefreshRepository;
import com.mjsec.ctf.repository.UserRepository;
import com.mjsec.ctf.filter.JwtFilter;
import com.mjsec.ctf.service.IPBanService;
import com.mjsec.ctf.service.JwtService;
import com.mjsec.ctf.service.ThreatDetectionService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final RefreshRepository refreshRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final IPBanService ipBanService;
    private final ThreatDetectionService threatDetectionService;

    public SecurityConfig(JwtService jwtService, RefreshRepository refreshRepository, UserRepository userRepository,
                          PasswordEncoder passwordEncoder, BlacklistedTokenRepository blacklistedTokenRepository,
                          IPBanService ipBanService, ThreatDetectionService threatDetectionService) {
        this.jwtService = jwtService;
        this.refreshRepository = refreshRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.blacklistedTokenRepository = blacklistedTokenRepository;
        this.ipBanService = ipBanService;
        this.threatDetectionService = threatDetectionService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http.cors(corsCustomizer -> corsCustomizer.configurationSource(new CorsConfigurationSource() {
            @Override
            public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOriginPatterns(
                        Arrays.asList("http://localhost:3000", "https://msgctf.kr", "https://www.msgctf.kr")
                );
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                configuration.setAllowCredentials(true);
                configuration.setAllowedHeaders(Arrays.asList(
                        "Authorization", "Content-Type", "Set-Cookie", "X-Requested-With", "Accept", "Origin"
                ));
                configuration.setExposedHeaders(Arrays.asList("Authorization", "access", "X-Custom-Header"));
                configuration.setMaxAge(3600L);
                return configuration;
            }
        }));

        // Stateless API 기본 설정
        http.csrf(csrf -> csrf.disable());
        http.formLogin(form -> form.disable());
        http.httpBasic(basic -> basic.disable());

        // IP 밴 필터 (최우선 순위로 등록)
        http.addFilterBefore(new IPBanFilter(ipBanService), UsernamePasswordAuthenticationFilter.class);

        // JWT 필터
        http.addFilterBefore(new CustomLoginFilter(userRepository, refreshRepository, jwtService, passwordEncoder, threatDetectionService),
                    UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new JwtFilter(jwtService, blacklistedTokenRepository),
                    UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new CustomLogoutFilter(jwtService, refreshRepository, blacklistedTokenRepository),
                    LogoutFilter.class);

        // 공격 탐지 필터 (JWT 인증 이후 실행하여 사용자 정보 추출 가능)
        http.addFilterAfter(new ThreatDetectionFilter(threatDetectionService), JwtFilter.class);

        // 인가 규칙
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/swagger-ui/*", "/v3/api-docs/**").permitAll()
            .requestMatchers("/api/users/**").permitAll()
            .requestMatchers("/api/leaderboard/**").permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .requestMatchers("/api/users/profile").hasAnyRole("USER","ADMIN")
            .requestMatchers("/api/reissue").permitAll()
            .requestMatchers("/").permitAll()
            .requestMatchers("/api/challenges/**").hasAnyRole("USER", "ADMIN")
            .requestMatchers("/api/payment/qr-token").hasAnyRole("USER", "ADMIN")
            .requestMatchers("/api/payment/checkout").hasRole("ADMIN")
            .requestMatchers("/api/payment/history").hasAnyRole("USER", "ADMIN")
            .requestMatchers("/api/team/profile").hasAnyRole("USER", "ADMIN")
            .requestMatchers("/api/team/history").hasAnyRole("USER", "ADMIN")
            .requestMatchers("/api/server-time").permitAll()

            // 🔐 Signature: 사용자용 엔드포인트 허용
            .requestMatchers(HttpMethod.POST, "/api/signature/*/check").hasAnyRole("USER","ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/signature/*/unlock").hasAnyRole("USER","ADMIN")
            .requestMatchers(HttpMethod.GET,  "/api/signature/*/status").hasAnyRole("USER","ADMIN")
            .requestMatchers(HttpMethod.GET,  "/api/signature/unlocked").hasAnyRole("USER","ADMIN")

            // (옵션) CORS preflight 허용
            .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()

            // 그 외 시그니처 API는 기존처럼 ADMIN 전용 유지
            .requestMatchers("/api/signature/**").hasRole("ADMIN")

            .requestMatchers("/api/contest-time").permitAll()
        );

        return http.build();
    }
}
