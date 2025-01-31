package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.dto.USER.UserDTO;
import com.mjsec.ctf.type.UserRole;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.repository.UserRepository;
import com.mjsec.ctf.type.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    //회원가입
    public void signUp(UserDTO.SignUp request) {
        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new RestApiException(ErrorCode.BAD_REQUEST);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RestApiException(ErrorCode.BAD_REQUEST);
        }

        // 요청의 roles 값 확인
        String roles = request.getRoles() != null && !request.getRoles().isEmpty()
                ? request.getRoles().get(0).toLowerCase() // roles가 리스트이므로 첫 번째 값 사용
                : "user"; // 기본값 설정

        // roles 값 유효성 검사
        if (!roles.equals("user") && !roles.equals("admin")) {
            throw new RestApiException(ErrorCode.BAD_REQUEST);
        }

        /* roles 타입이 UserEntity였을 때
        // ✅ 요청에서 roles이 없거나 비어 있으면 기본값 설정
        List<UserRole> userRoles;
        if (request.getRoles() == null || request.getRoles().isEmpty()) {
            userRoles = new ArrayList<>(List.of(UserRole.ROLE_USER)); // 기본값 설정
        } else {
            userRoles = request.getRoles().stream()
                    .map(UserRole::valueOf) // String -> Enum 변환
                    .collect(Collectors.toList());
        }
         */

        //유저 정보 저장
        UserEntity user = UserEntity.builder()
                .loginId(request.getLoginId())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .univ(request.getUniv())
                .roles(roles)
                .totalPoint(0)
                .build();

        userRepository.save(user);
    }

    public Map<String, Object> signIn(UserDTO.SignIn request){

        // 로그인 아이디로 유저 조회
        UserEntity user = userRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new RestApiException(ErrorCode.BAD_REQUEST));

        // 비밀번호 확인
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RestApiException(ErrorCode.BAD_REQUEST);
        }
        /* roles를 UserEntity로 썼을 때
        List<String> roles = user.getRoles().stream()
                .map(Enum::name)
                .collect(Collectors.toList());

        // JWT 토큰 생성
        String token = jwtService.createJwt("access", user.getLoginId(), roles, 3600000L); // 1시간 만료
         */

        final long ACCESS_TOKEN_EXPIRY = 3600000L; // 1시간
        final long REFRESH_TOKEN_EXPIRY = 604800000L; // 1주일

        // JWT 토큰 생성 - 제대로 구현 미지수 (더 추가 예정)
        String accessToken = jwtService.createJwt("access", user.getLoginId(), List.of(user.getRoles()), ACCESS_TOKEN_EXPIRY); // 1시간 만료

        String refreshToken = jwtService.createJwt("refresh", user.getLoginId(),List.of(user.getRoles()),REFRESH_TOKEN_EXPIRY);//일주일 만료

        /*
        accessToken과 refreshToken을 둘 다 사용함.
        accessToken은 Authentication Bearer <Token>
        refreshToken은 Cookie에 저장함
         */

        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getResponse();
        if (response != null) {
            Cookie refreshCookie = new Cookie("refreshToken", refreshToken);
            refreshCookie.setHttpOnly(true);
            refreshCookie.setSecure(true); // HTTPS 사용 시 true로 설정
            refreshCookie.setPath("/");
            refreshCookie.setMaxAge((int) REFRESH_TOKEN_EXPIRY / 1000); // 초 단위로 설정
            response.addCookie(refreshCookie);

            // Access Token은 헤더에 추가
            response.setHeader("access", accessToken);

            log.info("JWT added to response for user '{}'", user.getLoginId());
        }

        Map<String, Object> message = new HashMap<>();
        message.put("accessToken",accessToken);
        message.put("refreshToken",refreshToken);

        log.info("User '{}' signed in successfully", user.getLoginId());

        return message;
    }
    
    //로그아웃
    public void logout(String token) {
        try {
            // 1. 토큰이 만료되었는지 확인
            if (jwtService.isExpired(token)) {
                log.warn("Attempt to logout with expired token: {}", token);
                throw new RestApiException(ErrorCode.UNAUTHORIZED);
            }

            // 2. 토큰 타입이 "access"인지 확인
            String tokenType = jwtService.getTokenType(token);
            if (!"access".equals(tokenType)) {
                log.warn("Invalid token type used for logout: {}", tokenType);
                throw new RestApiException(ErrorCode.UNAUTHORIZED);
            }

            // 3. 로그아웃 처리
            log.info("Token invalidated: {}", token);

            // 4. Refresh Token 쿠키 삭제
            HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getResponse();
            if (response != null) {
                Cookie refreshCookie = new Cookie("refreshToken", null);
                refreshCookie.setHttpOnly(true);
                refreshCookie.setSecure(true); // HTTPS 사용 시
                refreshCookie.setPath("/");
                refreshCookie.setMaxAge(0); // 즉시 만료
                response.addCookie(refreshCookie);

                log.info("Refresh Token cleared from cookies");
            }

        } catch (Exception e) {
            log.error("Error occurred during logout: {}", e.getMessage());
            throw new RestApiException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}