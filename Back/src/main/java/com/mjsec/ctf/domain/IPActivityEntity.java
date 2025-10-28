package com.mjsec.ctf.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * IP별 활동 추적 엔티티 (공격 패턴 감지용)
 */
@Entity
@Table(name = "ip_activities", indexes = {
    @Index(name = "idx_ip_activity_time", columnList = "ipAddress,activityTime"),
    @Index(name = "idx_activity_time", columnList = "activityTime")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(builderMethodName = "doesNotUseThisBuilder")
public class IPActivityEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 45)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType activityType;

    @Column(nullable = false)
    private LocalDateTime activityTime;

    @Column(length = 500)
    private String requestUri;

    @Column(length = 1000)
    private String details;

    @Builder.Default
    @Column
    private Boolean isSuspicious = false;

    @Column
    private Long userId;

    @Column(length = 50)
    private String loginId;

    public enum ActivityType {
        LOGIN_SUCCESS,          // 로그인 성공
        LOGIN_FAILED,           // 로그인 실패
        FLAG_SUBMIT_WRONG,      // 플래그 오답
        API_REQUEST,            // API 요청
        SUSPICIOUS_PAYLOAD,     // 의심스러운 페이로드
        NOT_FOUND_ACCESS,       // 404 접근
        RATE_LIMIT_EXCEEDED     // Rate Limit 초과
    }
}
