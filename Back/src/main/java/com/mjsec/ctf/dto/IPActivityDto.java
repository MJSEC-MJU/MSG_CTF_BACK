package com.mjsec.ctf.dto;

import com.mjsec.ctf.domain.IPActivityEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * IP 활동 로그 DTO
 */
public class IPActivityDto {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String ipAddress;
        private String activityType;
        private LocalDateTime activityTime;
        private String requestUri;
        private String details;
        private Boolean isSuspicious;
        private Long userId;
        private String loginId;

        public static Response from(IPActivityEntity entity) {
            return Response.builder()
                    .id(entity.getId())
                    .ipAddress(entity.getIpAddress())
                    .activityType(entity.getActivityType().name())
                    .activityTime(entity.getActivityTime())
                    .requestUri(entity.getRequestUri())
                    .details(entity.getDetails())
                    .isSuspicious(entity.getIsSuspicious())
                    .userId(entity.getUserId())
                    .loginId(entity.getLoginId())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchRequest {
        private String ipAddress;      // 특정 IP 필터링 (선택)
        private String activityType;   // 활동 타입 필터링 (선택)
        private Boolean isSuspicious;  // 의심스러운 활동만 (선택)
        private Integer limit;         // 최대 개수 (기본 100)
    }

    /**
     * 의심스러운 IP 집계 DTO
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuspiciousIPSummary {
        private String ipAddress;              // IP 주소
        private Long suspiciousCount;          // 의심 활동 횟수
        private LocalDateTime lastActivityTime; // 마지막 활동 시간
        private String lastLoginId;            // 마지막 로그인 ID
        private String lastActivityType;       // 마지막 활동 유형
        private String lastDetails;            // 마지막 활동 상세
        private Boolean isBanned;              // 현재 차단 여부
    }
}
