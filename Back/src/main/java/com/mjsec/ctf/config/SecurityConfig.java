    package com.mjsec.ctf.config;

    import com.mjsec.ctf.jwt.CustomLoginFilter;
    import com.mjsec.ctf.jwt.CustomLogoutFilter;
    import com.mjsec.ctf.repository.BlacklistedTokenRepository;
    import com.mjsec.ctf.repository.RefreshRepository;
    import com.mjsec.ctf.repository.UserRepository;
    import com.mjsec.ctf.security.JwtFilter;
    import com.mjsec.ctf.service.JwtService;
    import jakarta.servlet.http.HttpServletRequest;
    import java.util.Arrays;
    import java.util.Collections;

    import lombok.extern.slf4j.Slf4j;
    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;
    import org.springframework.security.config.annotation.web.builders.HttpSecurity;
    import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
    import org.springframework.security.config.http.SessionCreationPolicy;
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

        public SecurityConfig(JwtService jwtService, RefreshRepository refreshRepository, UserRepository userRepository,PasswordEncoder passwordEncoder,BlacklistedTokenRepository blacklistedTokenRepository) {
            this.jwtService = jwtService;
            this.refreshRepository = refreshRepository;
            this.userRepository = userRepository;
            this.passwordEncoder = passwordEncoder;
            this.blacklistedTokenRepository = blacklistedTokenRepository;
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

            http
                    .cors(corsCustomizer -> corsCustomizer.configurationSource(new CorsConfigurationSource() {
                        @Override
                        public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {

                            CorsConfiguration configuration = new CorsConfiguration();

                            /*configuration.setAllowedOrigins(
                                    Arrays.asList("http://localhost:3000","https://msg.mjsec.kr")); // 배포시에는 변경될 주소 (테스트 비활성화)
                             */
                            configuration.setAllowedOriginPatterns(Arrays.asList("http://localhost:3000", "https://msg.mjsec.kr"));
                            //configuration.setAllowedMethods(Collections.singletonList("*")); //테스트로 잠시 비활성화
                            configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                            configuration.setAllowCredentials(true);
                            //configuration.setAllowedHeaders(Collections.singletonList("*")); //테스트로 잠시 비활성화
                            configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Set-Cookie", "X-Requested-With", "Accept"));
                            configuration.setMaxAge(3600L); // 브라우저가 CORS 관련 정보를 캐시할 시간을 설정
                            configuration.setExposedHeaders(Arrays.asList("Authorization", "access", "X-Custom-Header"));

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
                    .addFilterAfter(new JwtFilter(jwtService,blacklistedTokenRepository), UsernamePasswordAuthenticationFilter.class);
            http
                    .addFilterBefore(new CustomLoginFilter(userRepository, refreshRepository, jwtService, passwordEncoder), UsernamePasswordAuthenticationFilter.class)
                    .addFilterBefore(new CustomLogoutFilter(jwtService, refreshRepository,blacklistedTokenRepository), LogoutFilter.class);

            // 경로별 인가 작업
            http
                    .authorizeHttpRequests((auth) -> auth
                            .requestMatchers("/swagger-ui/*", "/v3/api-docs/**").permitAll()
                            .requestMatchers("/api/users/sign-up").permitAll() // 임시로 회원가입 테스트용 허용
                            .requestMatchers("/api/users/sign-in").permitAll()
                            .requestMatchers("/api/users/check-id").permitAll()
                            .requestMatchers("/api/users/check-email").permitAll()
                            //.requestMatchers("/api/users/logout").authenticated() // 로그아웃은 인증된 사용자만 가능
                            .requestMatchers("/api/admin/**").hasRole("ADMIN")  //어드민 접근근
                            .requestMatchers("/api/users/profile").authenticated()
                            .requestMatchers("/api/users/profile").hasAnyRole("admin","user")
                            .requestMatchers("/reissue").permitAll() //토큰 재생성
                            .requestMatchers("/").permitAll()
                            .requestMatchers("/api/leaderboard").permitAll()
                            .requestMatchers("/api/leaderboard/stream").permitAll()
                    );
            //세션 설정 : STATELESS (JWT 기반 인증을 사용하는 경우, 서버는 클라이언트의 상태를 유지할 필요가 없음)
            http
                    .sessionManagement((session) -> session
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

            //세션 로그인 설정 (최대 로그인 개수 3개까지 가능)
            http
                    .sessionManagement((auth)->auth
                            .maximumSessions(3)
                            .maxSessionsPreventsLogin(true));

            //로그인 시 동일한 세션에 대한 id 변경
            http
                    .sessionManagement((auth)->auth
                            .sessionFixation().changeSessionId());
            return http.build();
        }
    }