package com.mjsec.ctf.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

public class SignatureDto {

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Request {
        /** 팀명/클럽은 더이상 필요 없음. 6자리 코드만 받음 */
        @NotBlank
        @Pattern(regexp = "^[0-9]{6}$", message = "시그니처 코드는 6자리 숫자여야 합니다.")
        @JsonAlias({"code","signature"})
        private String signature;
    }

    @Data @AllArgsConstructor @NoArgsConstructor @Builder
    public static class CheckResponse {
        private boolean valid;
        private boolean unlocked;
        private Long teamId;
        private Long challengeId;
    }

    @Data @AllArgsConstructor @NoArgsConstructor @Builder
    public static class StatusResponse {
        private boolean unlocked;
        private Long teamId;
        private Long challengeId;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UnlockedListResponse {
        private Long teamId;
        private java.util.List<Long> challengeIds;
    }
}
