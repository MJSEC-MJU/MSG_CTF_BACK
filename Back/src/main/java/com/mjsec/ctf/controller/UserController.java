package com.mjsec.ctf.controller;

import com.mjsec.ctf.dto.HistoryDto;
import com.mjsec.ctf.dto.SuccessResponse;
import com.mjsec.ctf.dto.user.UserDto;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.repository.UserRepository;
import com.mjsec.ctf.service.AuthCodeService;
import com.mjsec.ctf.service.EmailService;
import com.mjsec.ctf.service.UserService;
import com.mjsec.ctf.type.ErrorCode;
import com.mjsec.ctf.type.ResponseMessage;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final EmailService emailService;
    private final AuthCodeService authCodeService;
    private final UserRepository userRepository;


    @Operation(summary = "회원가입", description = "유저 등록")
    @PostMapping("/sign-up")
    public ResponseEntity<SuccessResponse<Void>> signUp(@RequestBody @Valid UserDto.SignUp request) {

        userService.signUp(request); // 회원가입 서비스 호출

        return ResponseEntity.status(201).body(SuccessResponse.of(ResponseMessage.SIGNUP_SUCCESS));
    }

    @Operation(summary = "ID 확인", description = "해당 ID 사용 여부 확인 API")
    @GetMapping("/check-id")
    public ResponseEntity<Map<String, String>> checkLoginId(@RequestParam String loginId) {

        userService.checkLoginId(loginId);

        return ResponseEntity.ok(Map.of("message", "사용 가능한 아이디입니다."));
    }

    @Operation(summary = "이메일 확인", description = "해당 이메일 사용 여부 확인 API")
    @GetMapping("/check-email")
    public ResponseEntity<Map<String, String>> checkEmail(@RequestParam String email) {
        userService.checkEmail(email);

        return ResponseEntity.ok(Map.of("message","사용 가능한 이메일입니다."));
    }

    @Operation(summary = "유저 프로필 조회", description = "JWT 토큰을 이용해 프로필 조회")
    @GetMapping("/profile")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> getProfile(
            @RequestHeader(value = "Authorization") String token) {

        if (token == null || !token.startsWith("Bearer ")) {
            log.error("Authorization header is missing or invalid: {}", token);
            throw new RestApiException(ErrorCode.UNAUTHORIZED);
        }

        String accessToken = token.substring(7); // "Bearer " 이후의 토큰만 추출
        log.info("Extracted Access Token for profile: {}", accessToken);

        Map<String, Object> response = userService.getProfile(accessToken);
        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.PROFILE_SUCCESS, response));
    }

    @Operation(summary = "유저 이메일 인증 코드 보내기", description = "해당하는 학교 이메일만 인증 코드 보내기")
    @PostMapping("/send-code")
    public ResponseEntity<String> sendAuthCode(@RequestParam String email) {
        userService.checkEmail(email);

        String code = authCodeService.generateAndStoreCode(email);
        emailService.sendVerificationEmail(email, code);

        return ResponseEntity.ok("인증 코드가 전송되었습니다.");
    }

    // 인증 코드 검증 API
    @PostMapping("/verify-code")
    public ResponseEntity<String> verifyAuthCode(@RequestParam String email, @RequestParam String code) {
        boolean isValid = authCodeService.verifyCode(email, code);
        if (isValid) {
            return ResponseEntity.ok("이메일 인증이 완료되었습니다.");
        } else {
            throw new RestApiException(ErrorCode.FAILED_VERIFICATION);
        }
    }

    @Operation(summary = "히스토리 리스트", description = "유저별 푼 문제 리스트 확인")
    @GetMapping("/challenges")
    public ResponseEntity<SuccessResponse<List<HistoryDto>>> getChallengeHistory(
            @RequestHeader(value = "Authorization") String token) {

        if (token == null || !token.startsWith("Bearer ")) {
            log.error("Authorization header is missing or invalid: {}", token);
            throw new RestApiException(ErrorCode.UNAUTHORIZED);
        }

        String accessToken = token.substring(7);
        log.info("Extracted Access Token for profile: {}", accessToken);

        List<HistoryDto> history = userService.getChallengeHistory(accessToken);

        return ResponseEntity.status(HttpStatus.OK).body(
                SuccessResponse.of(
                        ResponseMessage.GET_HISTORY_SUCCESS,
                        history
                )
        );
    }
}