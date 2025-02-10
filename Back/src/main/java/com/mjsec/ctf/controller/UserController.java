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
    /*
    @Operation(summary = "로그인", description = "로그인")
    @PostMapping("/sign-in")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> signIn(@RequestBody @Valid UserDTO.SignIn request){
        Map<String, Object> response = userService.signIn(request);
        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.LOGIN_SUCCESS, response));
    }
    */

    /*
    @Operation(summary = "로그아웃", description = "유저 로그아웃")
    @PostMapping("/logout")
    public ResponseEntity<SuccessResponse<Void>> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            log.error("Authorization header is missing or invalid: {}", token);
            throw new RestApiException(ErrorCode.UNAUTHORIZED);
        }

        String accessToken = token.substring(7); // "Bearer " 이후의 토큰만 추출
        log.info("Extracted Access Token for logout: {}", accessToken);

        userService.logout(accessToken); // 🚀 로그아웃 서비스 호출
        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.LOGOUT_SUCCESS));
    }
    */

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