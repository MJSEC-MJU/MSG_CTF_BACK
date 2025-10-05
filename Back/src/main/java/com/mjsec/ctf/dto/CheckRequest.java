package com.mjsec.ctf.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CheckRequest {

    @NotBlank(message = "club은 필수입니다.")
    private String club;

    // 영문 대소문자/숫자 6자리
    @NotBlank(message = "value는 필수입니다.")
    @Pattern(regexp = "^[A-Za-z0-9]{6}$", message = "value는 영문/숫자 6자리여야 합니다.")
    private String value;
}
