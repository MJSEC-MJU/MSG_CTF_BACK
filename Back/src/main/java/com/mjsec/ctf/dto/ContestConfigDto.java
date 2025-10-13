package com.mjsec.ctf.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mjsec.ctf.domain.ContestConfigEntity;
import java.time.ZonedDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContestConfigDto {

    private Long id;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private ZonedDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private ZonedDateTime endTime;

    private Boolean isActive;

    public static ContestConfigDto fromEntity(ContestConfigEntity entity) {
        return ContestConfigDto.builder()
                .id(entity.getId())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .isActive(entity.getIsActive())
                .build();
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private ZonedDateTime startTime;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private ZonedDateTime endTime;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private ZonedDateTime startTime;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private ZonedDateTime endTime;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private ZonedDateTime currentTime;

        public static Response fromEntity(ContestConfigEntity entity, ZonedDateTime currentTime) {
            return Response.builder()
                    .startTime(entity.getStartTime())
                    .endTime(entity.getEndTime())
                    .currentTime(currentTime)
                    .build();
        }
    }
}
