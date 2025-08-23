package com.mjsec.ctf.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.time.LocalDateTime;

public class UserDTO {

    @Data
    public static class SignIn {
        @NotBlank(message = "로그인 아이디를 입력해주세요.")
        private String loginId;

        @NotBlank(message = "비밀번호를 입력해주세요.")
        private String password;
    }

    @Data
    public static class SignUp {
        @NotBlank(message = "로그인 아이디를 입력해주세요.") //비밀번호 규칙 있으면 수정할 예정
        private String loginId;

        @NotBlank(message = "비밀번호를 입력해주세요.")
        private String password;

        @Email(message = "이메일을 입력해주세요.")
        private String email;

        @NotBlank(message = "학교명을 입력해주세요.")
        private String univ;
        //"ROLE_USER" 또는 "ROLE_ADMIN"
        private String role;

    }
    //어드민으로 계정 변경시
    @Data
    public static class Update {
        // 업데이트 시 필요한 필드
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        private String email;
        
        @NotBlank(message = "대학 정보는 필수입니다.")
        private String univ;
        
        // 로그인 아이디 업데이트
        private String loginId;
        
        // 비밀번호 업데이트 (관리자에 의한 변경)
        private String password;
        
        // 역할 업데이트
        private String role;
    }
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Response {
        private Long userId;
        private String email;
        private String loginId;
        private String role;
        private Integer totalPoint;
        private String univ;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}