package com.mjsec.ctf.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

public class SignatureAdminDto {

    // Bulk 업서트용
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class UpsertRequest {
        @NotBlank private String teamName;
        @NotNull  private Long challengeId;
        @NotBlank @Size(min = 6, max = 6)
        private String code;
    }

    // 랜덤 생성 요청
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GenerateRequest {
        @NotNull private Long challengeId;
        @Min(1) @Max(10000) private int count;
        /** 특정 팀에 바로 배정하고 싶으면 teamName 지정, 아니면 null/빈값 */
        private String teamName;
    }

    // 생성 결과(평문 코드 포함)
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class GenerateResponse {
        private Long challengeId;
        private Long assignedTeamId;   // null 가능
        private int created;
        private List<String> codes;    // 평문 6자리 코드 목록
    }

    // 풀 조회(평문 없음)
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PoolItem {
        private Long id;
        private String codeDigest;
        private Long assignedTeamId;
        private boolean consumed;
        private LocalDateTime consumedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PoolListResponse {
        private Long challengeId;
        private List<PoolItem> items;
    }

    // 코드 재배정/초기화 요청
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ReassignRequest {
        @NotNull private Long challengeId;
        @NotBlank private String codeDigest;   // SHA-256 hex (64)
        /** 팀 재배정. null/빈값이면 배정 해제 */
        private String teamName;
        /** true면 consumed=false 및 consumedAt=null 로 초기화 */
        private Boolean resetConsumed;
    }

    // 강제 언락 요청(응급용)
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ForceUnlockRequest {
        @NotNull  private Long challengeId;
        @NotBlank private String teamName;
    }
}
