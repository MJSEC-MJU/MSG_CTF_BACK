package com.mjsec.ctf.dto;

import com.mjsec.ctf.domain.IPBanEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

public class IPBanDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BanRequest {
        @NotBlank(message = "IP 주소는 필수입니다")
        private String ipAddress;

        @NotBlank(message = "차단 사유는 필수입니다")
        private String reason;

        @NotNull(message = "차단 유형은 필수입니다")
        private IPBanEntity.BanType banType;

        // TEMPORARY인 경우 필수 (분 단위)
        private Long durationMinutes;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtendRequest {
        @NotNull(message = "연장 시간은 필수입니다")
        private Long additionalMinutes;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String ipAddress;
        private String reason;
        private IPBanEntity.BanType banType;
        private LocalDateTime bannedAt;
        private LocalDateTime expiresAt;
        private String bannedByAdminLoginId;
        private Boolean isActive;
        private Boolean isBanned;

        public static Response from(IPBanEntity entity) {
            return new Response(
                entity.getId(),
                entity.getIpAddress(),
                entity.getReason(),
                entity.getBanType(),
                entity.getBannedAt(),
                entity.getExpiresAt(),
                entity.getBannedByAdminLoginId(),
                entity.getIsActive(),
                entity.isBanned()
            );
        }
    }
}
