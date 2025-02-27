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

        public static Simple fromEntity(ChallengeEntity challenge) {
            return Simple.builder()
                    .challengeId(challenge.getChallengeId())
                    .title(challenge.getTitle())
                    .points(challenge.getPoints())
                    .solvers(challenge.getSolvers())
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Detail {

        private Long challengeId;
        private String description;
        private String url;
        private int points;
        private int solvers;

        public static Detail fromEntity(ChallengeEntity challenge) {
            return Detail.builder()
                    .challengeId(challenge.getChallengeId())
                    .description(challenge.getDescription())
                    .points(challenge.getPoints())
                    .url(challenge.getUrl())
                    .solvers(challenge.getSolvers())
                    .build();
        }
    }
}

