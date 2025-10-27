package com.mjsec.ctf.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class IPWhitelistDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddRequest {
        @NotBlank(message = "IP 주소는 필수입니다")
        private String ipAddress;

        @NotBlank(message = "화이트리스트 추가 사유는 필수입니다")
        private String reason;
    }
}
