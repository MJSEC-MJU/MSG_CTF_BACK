package com.mjsec.ctf.dto.USER;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

public class UserDTO {

    @Data
    public static class SignIn {
        @NotBlank(message = "로그인 아이디는 필수입니다.")
        private String loginId;

        @NotBlank(message = "비밀번호는 필수입니다.")
        private String password;
    }

    @Data
    public static class SignUp {
        @NotBlank(message = "로그인 아이디는 필수입니다.") //비밀번호 규칙 있으면 수정할 예정
        private String loginId;

        @NotBlank(message = "비밀번호는 필수입니다.")
        private String password;

        @Email(message = "유효한 이메일 주소를 입력해주세요.")
        private String email;

        @NotBlank(message = "소속 학교는 필수입니다.")
        private String univ;

        private List<String> roles; //user 또는 admin

    }
}