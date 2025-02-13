package com.mjsec.ctf.controller;

import com.mjsec.ctf.dto.SuccessResponse;
import com.mjsec.ctf.dto.USER.UserDTO;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.service.UserService;
import com.mjsec.ctf.type.ErrorCode;
import com.mjsec.ctf.type.ResponseMessage;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Operation(summary = "회원가입", description = "유저 등록")
    @PostMapping("/sign-up")
    public ResponseEntity<SuccessResponse<Void>> signUp(@RequestBody @Valid UserDTO.SignUp request) {
        userService.signUp(request); // 🚀 회원가입 서비스 호출
        return ResponseEntity.status(201).body(SuccessResponse.of(ResponseMessage.SIGNUP_SUCCESS));
    }

    @Operation(summary = "ID 확인", description = "해당 ID 사용 여부 확인 API")
    @GetMapping("/check-id")
    public ResponseEntity<?> checkLoginId(@RequestParam String loginId) {
        boolean exists = userService.isLoginIdExists(loginId);
        if (exists) {
            throw new RestApiException(ErrorCode.DUPLICATE_ID);
        }
        return ResponseEntity.ok("사용 가능한 아이디입니다.");
    }

    @Operation(summary = "이메일 확인", description = "해당 이메일 사용 여부 확인 API")
    @GetMapping("/check-email")
    public ResponseEntity<?> checkEmail(@RequestParam String email) {
        if (!userService.isValidEmail(email)) {
            throw new RestApiException(ErrorCode.INVALID_EMAIL_FORMAT);
        }
        boolean exists = userService.isEmailExists(email);
        if (exists) {
            throw new RestApiException(ErrorCode.DUPLICATE_EMAIL);
        }
        return ResponseEntity.ok("사용 가능한 이메일입니다.");
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

}