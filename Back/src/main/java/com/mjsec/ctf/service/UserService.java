package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.ChallengeEntity;
import com.mjsec.ctf.domain.HistoryEntity;
import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.dto.HistoryDto;
import com.mjsec.ctf.dto.user.UserDTO;
import com.mjsec.ctf.repository.BlacklistedTokenRepository;
import com.mjsec.ctf.repository.ChallengeRepository;
import com.mjsec.ctf.repository.HistoryRepository;
import com.mjsec.ctf.repository.RefreshRepository;
import com.mjsec.ctf.repository.LeaderboardRepository;
import com.mjsec.ctf.type.UserRole;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.repository.UserRepository;
import com.mjsec.ctf.type.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
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
    private final ChallengeRepository challengeRepository;
    private final LeaderboardRepository leaderboardRepository;

    private static final String[] ALLOWED_DOMAINS = {"@mju.ac.kr", "@kku.ac.kr", "@sju.ac.kr"};

    //회원가입 로직
    public void signUp(UserDTO.SignUp request) {
        //입력 모두 들어갔는지 확인
        validateSignUp(request);

        //아이디 유효성 검사
        validateLoginId(request.getLoginId());

        //비밀번호 유효성 검사
        validatePassword(request.getPassword());

        if (!isAllowedDomain(request.getEmail())) {
            throw new RestApiException(ErrorCode.UNAUTHORIZED_EMAIL);
        }

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
                .role("ROLE_USER")
                .totalPoint(0)
                .build();

        userRepository.save(user);
    }

    //이메일 검사하기
    public void checkEmail(String email){
        if(!isEmailExists(email)){
            throw new RestApiException(ErrorCode.DUPLICATE_EMAIL);
        }

        if(!isAllowedDomain(email)){
            throw new RestApiException(ErrorCode.UNAUTHORIZED_EMAIL);
        }

        if(!isValidEmail(email)){
            throw new RestApiException(ErrorCode.INVALID_EMAIL_FORMAT);
        }
    }

    //로그인ID 검사하기
    public void checkLoginId(String loginId){
        validateLoginId(loginId);

        if(isLoginIdExists(loginId)){
            throw new RestApiException(ErrorCode.DUPLICATE_ID);
        }
    }

    //유저 프로필 반환
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
        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("userId", user.getUserId());
        userProfile.put("loginId", user.getLoginId());
        userProfile.put("email", user.getEmail());
        userProfile.put("univ", user.getUniv());
        userProfile.put("roles", user.getRole());
        userProfile.put("total_point", user.getTotalPoint());
        userProfile.put("created_at", user.getCreatedAt());
        userProfile.put("updated_at", user.getUpdatedAt());

        return userProfile;
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
        // 역할 수정 로직 추가
        if (updateDto.getRole() != null && !updateDto.getRole().isBlank()) {
            String roleStr = updateDto.getRole().toUpperCase();
            try {
                UserRole role = UserRole.valueOf(roleStr);
                user.setRole(role.toString());
            } catch (IllegalArgumentException e) {
                throw new RestApiException(ErrorCode.INVALID_ROLE);
            }
        }
        return userRepository.save(user); // 수정된 user 반환
    }

    //유저 삭제
    @Transactional
    public void deleteMember(Long userId) {

        log.info("deleteMember userId : {}", userId);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RestApiException(ErrorCode.BAD_REQUEST, "해당 회원이 존재하지 않습니다."));

        leaderboardRepository.findByLoginId(user.getLoginId())
                .ifPresent(leaderboardRepository::delete);

        List<HistoryEntity> historyEntities = historyRepository.findByLoginId(user.getLoginId());

        if (!historyEntities.isEmpty()) {
            historyEntities.forEach(history -> {
                history.anonymizeUser();
            });
            historyRepository.saveAll(historyEntities);
            log.info("History Entity list : {}", historyEntities.size());

            // 회원의 로그인 ID를 기준으로 히스토리 삭제
            historyRepository.deleteByLoginId(user.getLoginId());

            userRepository.delete(user);

            log.info("deleteMember userId : {} is deleted", userId);
        }
    }

    //삭제된 유저
    @Transactional(readOnly = true)
    public Map<String, Object> getDeletedUserStatistics() {
        List<HistoryEntity> deletedUserHistories = historyRepository.findByUserDeletedTrue();

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("deletedUserSolveCount", deletedUserHistories.size());
        statistics.put("schoolDistribution", deletedUserHistories.stream()
                .collect(Collectors.groupingBy(
                        HistoryEntity::getUniv,
                        Collectors.counting()
                )));

        return statistics;
    }

    public List<HistoryDto> getChallengeHistory(String accessToken) {
        String loginId = jwtService.getLoginId(accessToken);

        UserEntity user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

        // 삭제되지 않은 사용자의 기록만 조회
        List<HistoryEntity> historyEntities = historyRepository.findByLoginIdAndUserDeletedFalse(user.getLoginId());

        if (historyEntities == null || historyEntities.isEmpty()) {
            return Collections.emptyList();
        }

        return historyEntities.stream()
                .map(historyEntity -> {
                    ChallengeEntity challenge = challengeRepository.findById(historyEntity.getChallengeId())
                            .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));

                    return new HistoryDto(
                            historyEntity.getLoginId(),
                            historyEntity.getChallengeId().toString(),
                            challenge.getTitle(),
                            historyEntity.getSolvedTime(),
                            challenge.getPoints(),
                            historyEntity.getUniv()
                    );
                })
                .collect(Collectors.toList());
    }

    //어드민 회원가입
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
        if (request.getRole() != null && !request.getRole().isBlank()) {
            // 입력값을 대문자로 변환하여 표준 형식으로 맞춥니다.
            String inputRole = request.getRole().toUpperCase();
            if (!inputRole.equals("ROLE_USER") && !inputRole.equals("ROLE_ADMIN")) {
                throw new RestApiException(ErrorCode.INVALID_ROLE);
            }
            role = inputRole;
        } else {
            role = "ROLE_USER";
        }

        UserEntity user = UserEntity.builder()
                .loginId(request.getLoginId())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .univ(request.getUniv())
                .role(role)  // 전달된 역할로 설정 (관리자 생성 시 "admin" 입력 가능)
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

    /*
        검증용 메서드들
     */
    private void validateSignUp(UserDTO.SignUp request){
        if (request.getLoginId() == null || request.getLoginId().trim().isEmpty()) {
            throw new RestApiException(ErrorCode.EMPTY_LOGIN_ID);
        }

        if(request.getUniv() == null || request.getUniv().trim().isEmpty()){
            throw new RestApiException(ErrorCode.EMPTY_UNIV);
        }

        if(request.getEmail() == null || request.getEmail().trim().isEmpty()){
            throw new RestApiException(ErrorCode.EMPTY_EMAIL);
        }

        if(request.getPassword() == null || request.getPassword() .trim().isEmpty()){
            throw new RestApiException(ErrorCode.EMPTY_PASSWORD);
        }
    }

    private void validatePassword(String password) {
        // 공백 포함 여부 확인 (문자열 내부나 앞뒤에 공백 포함 불가)
        if (password.contains(" ")) {
            throw new RestApiException(ErrorCode.INVALID_PASSWORD_WHITESPACE);
        }

        // 최소 길이 검사 (8자 이상)
        if (password.length() < 8) {
            throw new RestApiException(ErrorCode.INVALID_PASSWORD_LENGTH_MIN);
        }

        // 최대 길이 검사 (32자 이하)
        if (password.length() > 32) {
            throw new RestApiException(ErrorCode.INVALID_PASSWORD_LENGTH_MAX);
        }

        // 소문자, 대문자, 숫자 및 특수문자 포함 여부 확인
        String passwordPattern = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*]).{8,32}$";
        if (!Pattern.matches(passwordPattern, password)) {
            throw new RestApiException(ErrorCode.INVALID_PASSWORD_FORMAT);
        }
    }

    private boolean isLoginIdExists(String loginId) {
        return userRepository.existsByLoginId(loginId);
    }

    private void validateLoginId(String loginId) {
        // 공백 포함 불가
        if (loginId.contains(" ")) {
            throw new RestApiException(ErrorCode.INVALID_ID_WHITESPACE);
        }

        // 길이 검사 (4~20자)
        if (loginId.length() < 4 ) {
            throw new RestApiException(ErrorCode.INVALID_ID_LENGTH_MIN);
        }
        if(loginId.length() > 20){
            throw new RestApiException(ErrorCode.INVALID_ID_LENGTH_MAX);
        }

        // 영문 + 숫자만 허용
        if (!Pattern.matches("^[a-zA-Z0-9]+$", loginId)) {
            throw new RestApiException(ErrorCode.INVALID_ID_CHARACTERS);
        }
    }

    private boolean isEmailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    // 이메일 유효성 검사 함수
    private boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        return email.matches(emailRegex);
    }


    // 허용된 도메인인지 검증
    private boolean isAllowedDomain(String email) {
        for (String domain : ALLOWED_DOMAINS) {
            if (email.endsWith(domain)) {
                return true;
            }
        }
        return false;
    }

}