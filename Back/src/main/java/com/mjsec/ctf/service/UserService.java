package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.HistoryEntity;
import com.mjsec.ctf.domain.RefreshEntity;
import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.dto.user.UserDTO;
import com.mjsec.ctf.repository.BlacklistedTokenRepository;
import com.mjsec.ctf.repository.HistoryRepository;
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
    private final AuthCodeService authCodeService;
    private final JwtService jwtService;
    private final RefreshRepository refreshRepository;
    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final HistoryRepository historyRepository;

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

        // 이메일 인증 여부 확인
        if (!authCodeService.isEmailVerified(request.getEmail())) {
            throw new RestApiException(ErrorCode.EMAIL_VERIFICATION_PENDING);
        }

        UserEntity user = UserEntity.builder()
                .loginId(request.getLoginId())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .univ(request.getUniv())
                .roles("ROLE_USER")
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

        // 유저가 푼 문제 리스트 조회
        List<HistoryEntity> historyEntities = historyRepository.findByUserId(user.getLoginId());

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

        //유저가 푼 문제 추가 (임시)
        response.put("history", historyEntities);

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
    public void deleteMember(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RestApiException(ErrorCode.BAD_REQUEST, "해당 회원이 존재하지 않습니다."));
        userRepository.delete(user);
    }

    @Transactional
    public void adminSignUp(UserDTO.SignUp request) {
        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new RestApiException(ErrorCode.DUPLICATE_ID);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RestApiException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (!isValidEmail(request.getEmail())) {
            throw new RestApiException(ErrorCode.INVALID_EMAIL_FORMAT);
        }
        
        // roles 값이 전달되면 해당 역할을 사용하고, 없으면 기본적으로 "user"로 설정합니다.
        String role;
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            // 예를 들어, 리스트의 첫번째 값을 사용합니다.
            role = request.getRoles().get(0).toLowerCase();
        } else {
            role = "ROLE_USER";
        }
        
        UserEntity user = UserEntity.builder()
                .loginId(request.getLoginId())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .univ(request.getUniv())
                .roles(role)  // 전달된 역할로 설정 (관리자 생성 시 "admin" 입력 가능)
                .totalPoint(0)
                .build();
        
        userRepository.save(user);
    }


    // **전체 사용자 목록 조회 **
    public List<UserEntity> getAllUsers() {
        return userRepository.findAll();
    }
    // ** id로 한 명의 사용자 조회 **
    @Transactional(readOnly = true)
    public UserEntity getUserById(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new RestApiException(ErrorCode.BAD_REQUEST, "해당 회원이 존재하지 않습니다."));
    }
}