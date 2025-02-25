package com.mjsec.ctf.controller;

import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.dto.SuccessResponse;
import com.mjsec.ctf.dto.USER.UserDTO;
import com.mjsec.ctf.service.UserService;
import com.mjsec.ctf.type.ResponseMessage;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    @Operation(summary = "회원정보 수정", description = "관리자 권한으로 특정 회원의 정보를 수정합니다. (비밀번호 변경 포함)")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/change/member/{userId}")
    public ResponseEntity<SuccessResponse<Void>> updateMember(
            @PathVariable Long userId,
            @RequestBody @Valid UserDTO.Update updateDto) {

        UserEntity updatedUser = userService.updateMember(userId, updateDto);
        log.info("관리자에 의해 회원 {} 정보 수정 완료", userId);
        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.UPDATE_SUCCESS));
    }
     // 관리자용 회원 삭제 API
    @Operation(summary = "회원 삭제", description = "관리자 권한으로 특정 회원을 삭제합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/delete/member/{userId}")
    public ResponseEntity<SuccessResponse<Void>> deleteMember(@PathVariable Long userId) {
        userService.deleteMember(userId);
        log.info("관리자에 의해 회원 {} 삭제 완료", userId);
        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.DELETE_SUCCESS));
    }
    @Operation(summary = "회원 추가 (관리자)", description = "관리자 권한으로 이메일 인증 없이 새로운 회원 계정을 생성합니다. (관리자 계정도 추가 가능)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/add/member")
    public ResponseEntity<SuccessResponse<Void>> addMember(@RequestBody @Valid UserDTO.SignUp newUser) {
        userService.adminSignUp(newUser);
        log.info("관리자에 의해 새로운 회원 추가 완료: {}", newUser.getLoginId());
        return ResponseEntity.status(201).body(SuccessResponse.of(ResponseMessage.SIGNUP_SUCCESS));
    }

}
