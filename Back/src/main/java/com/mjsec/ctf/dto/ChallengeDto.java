package com.mjsec.ctf.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mjsec.ctf.domain.ChallengeEntity;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeDto {

    private Long ChallengeId;

    private String title;

    private String description;

    private String flag;

    private int points;

    private int minPoints;

    private int initialPoints;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private String url;

    private String category;

    //모든 문제 조회용
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Simple {

        private Long challengeId;
        private int points;
        private int solvers;
        private String title;
        private String category;
        private boolean solved;

        public static Simple fromEntity(ChallengeEntity challenge, boolean solved) {
            return Simple.builder()
                    .challengeId(challenge.getChallengeId())
                    .title(challenge.getTitle())
                    .points(challenge.getPoints())
                    .solvers(challenge.getSolvers())
                    .category(challenge.getCategory() != null ? challenge.getCategory().toString() : null)
                    .solved(solved)
                    .build();
        }
    }

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
        private int solvers;
        private String category;

        public static Detail fromEntity(ChallengeEntity challenge) {
            return Detail.builder()
                    .challengeId(challenge.getChallengeId())
                    .title(challenge.getTitle())
                    .description(challenge.getDescription())
                    .points(challenge.getPoints())
                    .url(challenge.getUrl())
                    .solvers(challenge.getSolvers())
                    .category(challenge.getCategory() != null ? challenge.getCategory().toString() : null)
                    .build();
        }
    }
    @Data
    public static class SignaturePolicyDto {
        @NotBlank(message = "시그니처 이름은 공백일 수 없습니다.")
        private String name;

        @NotBlank(message = "시그니처 코드는 공백일 수 없습니다.")
        private String signature;

        @NotBlank(message = "소속 클럽은 공백일 수 없습니다.")
        private String club;
    }

    @Data
    public static class Request {
        // 기존 생성/수정 입력 필드들 (title, description, flag, start/endTime, url, points 등)
        private String title;
        private String description;
        private String flag;
        private String category;
        private Integer points;
        private Integer minPoints;
        private Integer initialPoints;
        private String url;
        private java.time.LocalDateTime startTime;
        private java.time.LocalDateTime endTime;

        // 카테고리 == SIGNATURE 일 때만 필수
        private SignaturePolicyDto signaturePolicy;
    }
}

