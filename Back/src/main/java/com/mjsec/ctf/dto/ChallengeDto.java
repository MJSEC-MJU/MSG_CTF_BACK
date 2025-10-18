package com.mjsec.ctf.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mjsec.ctf.domain.ChallengeEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeDto {

    private Long challengeId;   // 대소문자 정리
    private String title;
    private String description;
    private String flag;

    private int points;
    private int minPoints;
    private int initialPoints;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    private LocalDateTime endTime;

    private String url;
    private String category;

    private int mileage;

    //모든 문제 조회용

    /** 시그니처 문제 표시용 클럽명 (SIGNATURE일 때 필수) */
    private String club;

    // ─────────────────────────────────────────────────────────────────────────────
    /** 리스트 응답용 DTO */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Simple {
        private Long challengeId;
        private int points;
        private int mileage;
        private int solvers;
        private String title;
        private String category;
        private boolean solved;
        private String club;

        public static Simple fromEntity(ChallengeEntity challenge, boolean solved) {
            return Simple.builder()
                    .challengeId(challenge.getChallengeId())
                    .title(challenge.getTitle())
                    .points(challenge.getPoints())
                    .mileage(challenge.getMileage())
                    .solvers(challenge.getSolvers())
                    .category(challenge.getCategory() != null ? challenge.getCategory().toString() : null)
                    .solved(solved)
                    .club(challenge.getClub())
                    .build();
        }
    }

    /** 상세 응답용 DTO */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Detail {
        private Long challengeId;
        private String title;
        private String description;
        private String url;
        private int points;
        private int mileage;
        private int solvers;
        private String category;
        private String club;

        public static Detail fromEntity(ChallengeEntity challenge) {
            return Detail.builder()
                    .challengeId(challenge.getChallengeId())
                    .title(challenge.getTitle())
                    .description(challenge.getDescription())
                    .points(challenge.getPoints())
                    .mileage(challenge.getMileage())
                    .url(challenge.getUrl())
                    .solvers(challenge.getSolvers())
                    .category(challenge.getCategory() != null ? challenge.getCategory().toString() : null)
                    .club(challenge.getClub())
                    .build();
        }
    }

    /** (기존 유지) 시그니처 정책 입력 DTO - 현재는 미사용 가능 */
    @Data
    public static class SignaturePolicyDto {
        @NotBlank(message = "시그니처 이름은 공백일 수 없습니다.")
        private String name;

        @NotBlank(message = "시그니처 코드는 공백일 수 없습니다.")
        private String signature;

        @NotBlank(message = "소속 클럽은 공백일 수 없습니다.")
        private String club;
    }

    /** (기존 유지) 별도 Request DTO가 필요한 경우 사용 */
    @Data
    public static class Request {
        private String title;
        private String description;
        private String flag;
        private String category;
        private Integer points;
        private Integer minPoints;
        private Integer initialPoints;
        private String url;
        private LocalDateTime startTime;
        private LocalDateTime endTime;

        private SignaturePolicyDto signaturePolicy;
    }
}
