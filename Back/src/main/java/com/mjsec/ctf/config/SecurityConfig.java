package com.mjsec.ctf.config;

import com.mjsec.ctf.security.JwtFilter;
import com.mjsec.ctf.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;

    public SecurityConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .cors(corsCustomizer -> corsCustomizer.configurationSource(new CorsConfigurationSource() {
                    @Override
                    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {

                        CorsConfiguration configuration = new CorsConfiguration();

                        configuration.setAllowedOrigins(
                                Arrays.asList("http://localhost:3000")); // 배포시에는 변경될 주소
                        configuration.setAllowedMethods(Collections.singletonList("*"));
                        configuration.setAllowCredentials(true);
                        configuration.setAllowedHeaders(Collections.singletonList("*"));
                        configuration.setMaxAge(3600L); // 브라우저가 CORS 관련 정보를 캐시할 시간을 설정
                        configuration.setExposedHeaders(Arrays.asList("Set-Cookie", "Authorization", "access"));

                        return configuration;
                    }
                }));
        // csrf disable (RESTful API는 일반적으로 상태가 없고, 세션을 사용하지 않기 때문에 CSRF 공격에 덜 취약하기 때문)
        http
                .csrf((auth) -> auth.disable());
        // Form 로그인 방식 disable (폼 로그인은 세션을 사용하여 인증 상태를 유지하지만, JWT는 클라이언트 측에서 토큰을 저장하고 요청 시마다 토큰을 전송하여 인증을 처리하기 때문)
        http
                .formLogin((auth) -> auth.disable());
        // HTTP Basic 인증 방식 disable (HTTP Basic 인증은 사용자 이름과 비밀번호를 Base64로 인코딩하여 전송하는 방식으로, 보안이 취약할 수 있음)
        http
                .httpBasic((auth) -> auth.disable());
        //JWTFilter
        http
                .addFilterAfter(new JwtFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        // 경로별 인가 작업
        http
                .authorizeHttpRequests((auth) -> auth
                        .requestMatchers("/swagger-ui/*", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/").permitAll()
                );
        //세션 설정 : STATELESS (JWT 기반 인증을 사용하는 경우, 서버는 클라이언트의 상태를 유지할 필요가 없음)
        http
                .sessionManagement((session) -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}
