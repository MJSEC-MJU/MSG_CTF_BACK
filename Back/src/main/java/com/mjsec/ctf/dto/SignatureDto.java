package com.mjsec.ctf.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

public class SignatureDto {

    @Data
    public static class Request {
        @NotBlank private String name;
        @NotBlank private String signature;
        @NotBlank private String club;
    }

    @Data @AllArgsConstructor @NoArgsConstructor @Builder
    public static class CheckResponse {
        private boolean valid;       // 정책과 일치 여부
        private boolean unlocked;    // 팀×문제 잠금 해제 여부
        private Long teamId;
        private Long challengeId;
    }

    @Data @AllArgsConstructor @NoArgsConstructor @Builder
    public static class StatusResponse {
        private boolean unlocked;
        private Long teamId;
        private Long challengeId;
    }
}
