package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.RefreshEntity;
import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.dto.USER.UserDTO;
import com.mjsec.ctf.repository.BlacklistedTokenRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshRepository refreshRepository;
    private final BlacklistedTokenRepository blacklistedTokenRepository;

    //회원가입 로직
    public void signUp(UserDTO.SignUp request) {
        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new RestApiException(ErrorCode.DUPLICATE_ID);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RestApiException(ErrorCode.DUPLICATE_EMAIL);
        }

        // 이메일 형식 검사
        if (!isValidEmail(request.getEmail())) {
            throw new RestApiException(ErrorCode.INVALID_EMAIL_FORMAT);
        }

        // 요청의 roles 값 확인
        String roles = request.getRoles() != null && !request.getRoles().isEmpty()
                ? request.getRoles().get(0).toLowerCase() // roles가 리스트이므로 첫 번째 값 사용
                : "user"; // 기본값 설정

        // roles 값 유효성 검사
        if (!roles.equals("user") && !roles.equals("admin")) {
            throw new RestApiException(ErrorCode.BAD_REQUEST);
        }

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

    public boolean isLoginIdExists(String loginId) {
        return userRepository.existsByLoginId(loginId);
    }

    public boolean isEmailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    // 이메일 유효성 검사 함수
    public boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        return email.matches(emailRegex);
    }

    public Map<String, Object> getProfile(String token) {
        // 토큰 유효성 검사
        if (jwtService.isExpired(token)) {
            log.warn("다시 로그인하세요.(Access Token이 만료되었습니다.)");
            throw new RestApiException(ErrorCode.UNAUTHORIZED,"다시 로그인하세요.(Access Token이 만료되었습니다.)");
        }

        if (blacklistedTokenRepository.existsByToken(token)) {
            log.warn("다시 로그인하세요.(블랙리스트 설정됨)");
            throw new RestApiException(ErrorCode.UNAUTHORIZED,"다시 로그인하세요.(블랙리스트 설정됨)");
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
     // 관리자용 회원정보 수정 메서드
    @Transactional
    public UserEntity updateMember(Long userId, UserDTO.Update updateDto) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RestApiException(ErrorCode.BAD_REQUEST, "해당 회원이 존재하지 않습니다."));
        user.setEmail(updateDto.getEmail());
        user.setUniv(updateDto.getUniv());
        if (updateDto.getLoginId() != null && !updateDto.getLoginId().isBlank()) {
            user.setLoginId(updateDto.getLoginId());
        }
        if (updateDto.getPassword() != null && !updateDto.getPassword().isBlank()) {
            String encodedPassword = passwordEncoder.encode(updateDto.getPassword());
            user.setPassword(encodedPassword);
        }
        return userRepository.save(user);
    }

}