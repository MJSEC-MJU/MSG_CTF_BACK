package com.mjsec.ctf.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * IP 화이트리스트 엔티티
 * 관리자가 수동으로 추가한 IP는 절대 자동 차단되지 않음
 */
@Entity
@Table(name = "ip_whitelist")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class IPWhitelistEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 45)
    private String ipAddress;                    // IP 주소

    @Column(length = 500)
    private String reason;                       // 화이트리스트 추가 사유

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;             // 활성 여부

    private Long addedByAdminId;                 // 추가한 관리자 ID

    @Column(length = 50)
    private String addedByAdminLoginId;          // 추가한 관리자 로그인 ID

    private LocalDateTime addedAt;               // 추가 시간

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
