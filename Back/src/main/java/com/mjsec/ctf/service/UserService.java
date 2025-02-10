package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.RefreshEntity;
import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.dto.USER.UserDTO;
import com.mjsec.ctf.repository.RefreshRepository;
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

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshRepository refreshRepository;  // ✅ final 추가


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

    /*
    public Map<String, Object> signIn(UserDTO.SignIn request){


        // 로그인 아이디로 유저 조회
        UserEntity user = userRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new RestApiException(ErrorCode.BAD_REQUEST));

        // 비밀번호 확인
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RestApiException(ErrorCode.BAD_REQUEST);
        }

        final long ACCESS_TOKEN_EXPIRY = 3600000L; // 1시간
        final long REFRESH_TOKEN_EXPIRY = 43200000L; //

        // JWT 토큰 생성 - 제대로 구현 미지수 (더 추가 예정)
        String accessToken = jwtService.createJwt("accessToken", user.getLoginId(), List.of(user.getRoles()), ACCESS_TOKEN_EXPIRY); // 1시간 만료

        String refreshToken = jwtService.createJwt("refreshToken", user.getLoginId(),List.of(user.getRoles()),REFRESH_TOKEN_EXPIRY);

        addRefreshEntity(user.getLoginId(),refreshToken,REFRESH_TOKEN_EXPIRY);

        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getResponse();
        if (response != null) {
            Cookie refreshCookie = new Cookie("refreshToken", refreshToken);
            refreshCookie.setHttpOnly(true);
            refreshCookie.setSecure(true); // HTTPS 사용 시 true로 설정
            refreshCookie.setPath("/");
            refreshCookie.setMaxAge((int) REFRESH_TOKEN_EXPIRY / 1000); // 초 단위로 설정
            response.addCookie(refreshCookie);

            // Access Token은 헤더에 추가
            response.setHeader("accessToken", accessToken);

            log.info("JWT added to response for user '{}'", user.getLoginId());
        }

        Map<String, Object> message = new HashMap<>();
        message.put("accessToken",accessToken);
        message.put("refreshToken",refreshToken);

        log.info("User '{}' signed in successfully", user.getLoginId());

        return message;
    }

    
    //나중에 LoginFilter로 이동 예정
    private void addRefreshEntity(String loginId, String refresh, Long expiredMs) {

        Date date = new Date(System.currentTimeMillis() + expiredMs);

        RefreshEntity refreshEntity = new RefreshEntity();
        refreshEntity.setLoginId(loginId);
        refreshEntity.setRefresh(refresh);
        refreshEntity.setExpiration(date.toString());

        refreshRepository.save(refreshEntity);
    }
     */
        /* roles를 UserEntity로 썼을 때 (혹시 모르는 용도)
        List<String> roles = user.getRoles().stream()
                .map(Enum::name)
                .collect(Collectors.toList());

        // JWT 토큰 생성
        String token = jwtService.createJwt("accessToken", user.getLoginId(), roles, 3600000L); // 1시간 만료
     */

    /*
    public void logout(String token) {
        try {
            // 1. 토큰이 만료되었는지 확인
            if (jwtService.isExpired(token)) {
                log.warn("Attempt to logout with expired token: {}", token);
                throw new RestApiException(ErrorCode.UNAUTHORIZED);
            }

            // 2. 토큰 타입이 "accessToken"인지 확인
            String tokenType = jwtService.getTokenType(token);
            if (!"accessToken".equals(tokenType)) {
                log.warn("Invalid token type used for logout: {}", tokenType);
                throw new RestApiException(ErrorCode.UNAUTHORIZED);
            }

            // 3. 로그아웃 처리 (블랙리스트 추가 가능)
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
     */

    public Map<String, Object> getProfile(String token) {
        // 토큰 유효성 검사
        if (jwtService.isExpired(token)) {
            log.warn("Access Token이 만료되었습니다. 다시 로그인하세요.");
            throw new RestApiException(ErrorCode.UNAUTHORIZED);
        }

        // 토큰에서 로그인 ID 가져오기
        String loginId = jwtService.getLoginId(token);

        // 로그인 ID로 유저 조회
        UserEntity user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RestApiException(ErrorCode.BAD_REQUEST));

        // 프로필 정보 반환
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> userProfile = new HashMap<>();

        userProfile.put("user_id", user.getUserId());
        userProfile.put("email", user.getEmail());
        userProfile.put("univ", user.getUniv());
        userProfile.put("roles", user.getRoles());
        userProfile.put("total_point", user.getTotalPoint());
        userProfile.put("created_at", user.getCreatedAt());
        userProfile.put("updated_at", user.getUpdatedAt());

        response.put("user", userProfile);

        return response;
    }

}