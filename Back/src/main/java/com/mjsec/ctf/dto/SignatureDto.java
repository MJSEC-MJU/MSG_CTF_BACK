package com.mjsec.ctf.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

public class SignatureDto {

    @Data
    public static class Request {
        @NotBlank(message = "이름은 공백일 수 없습니다.")
        private String name;

        @NotBlank(message = "시그니처는 공백일 수 없습니다.")
        private String signature;

        @NotBlank(message = "클럽은 공백일 수 없습니다.")
        private String club;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CheckResponse {
        private boolean result;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class InsertResponse {
        private boolean result;

        // 생성된 엔티티 id (성공 시)
        private Long id;
    }
}
