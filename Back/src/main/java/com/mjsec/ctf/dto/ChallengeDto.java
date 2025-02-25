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

        private Long ChallengeId;
        private int points;

        public static Simple fromEntity(ChallengeEntity challenge) {
            return Simple.builder()
                    .ChallengeId(challenge.getChallengeId())
                    .points(challenge.getPoints())
                    .build();
        }
    }

    //각 문제 상세 정보 조회(API 명세내 solver 아직 구현 못함...)
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Detail {

        private Long challengeId;
        private String description;
        private String url;
        private int points;

        public static Detail fromEntity(ChallengeEntity challenge) {
            return Detail.builder()
                    .challengeId(challenge.getChallengeId())
                    .description(challenge.getDescription())
                    .points(challenge.getPoints())
                    .url(challenge.getUrl())
                    .build();
        }
    }
}

