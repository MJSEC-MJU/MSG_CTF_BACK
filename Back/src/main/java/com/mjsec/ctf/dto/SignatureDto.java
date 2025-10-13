package com.mjsec.ctf.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

public class SignatureDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Request {
        @NotBlank private String name;
        @NotBlank private String signature;
        @NotBlank private String club;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CheckResponse {
        // JSON 키를 기존과 동일하게 유지 ("return")
        @JsonProperty("return")
        private boolean result;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class InsertResponse {
        // 성공/중복 여부 표시 (기존 호환)
        @JsonProperty("return")
        private boolean result;
        // 생성된 엔티티 id까지 주면 추후 추적에 유용
        private Long id;
    }
}