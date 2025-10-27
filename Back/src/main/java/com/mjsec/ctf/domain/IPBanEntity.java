package com.mjsec.ctf.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "ip_bans", indexes = {
    @Index(name = "idx_ip_address", columnList = "ipAddress"),
    @Index(name = "idx_expires_at", columnList = "expiresAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(builderMethodName = "doesNotUseThisBuilder")
public class IPBanEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 45)
    private String ipAddress;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BanType banType;

    @Column(nullable = false)
    private LocalDateTime bannedAt;

    @Column
    private LocalDateTime expiresAt;

    @Column
    private Long bannedByAdminId;

    @Column(length = 100)
    private String bannedByAdminLoginId;

    @Builder.Default
    @Column
    private Boolean isActive = true;

    public enum BanType {
        TEMPORARY,
        PERMANENT
    }

    public boolean isExpired() {
        if (banType == BanType.PERMANENT) {
            return false;
        }
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isBanned() {
        return isActive && !isExpired();
    }
}
