package com.mjsec.ctf.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@SuperBuilder
@Table(name = "signature_code",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_sig_code_challenge_digest", columnNames = {"challenge_id", "code_digest"})
       },
       indexes = {
           @Index(name = "idx_sig_code_challenge", columnList = "challenge_id"),
           @Index(name = "idx_sig_code_team", columnList = "assigned_team_id")
       })
@SQLDelete(sql = "UPDATE signature_code SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at is null")
public class SignatureCodeEntity extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "challenge_id", nullable = false)
    private Long challengeId;

    /** 조회용 고정 다이제스트 (SHA-256 hex 64chars) */
    @Column(name = "code_digest", nullable = false, length = 64)
    private String codeDigest;

    /** 검증용 해시(BCrypt 등, 60~100자) */
    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    /** 사전 배정 팀 (nullable). null이면 선착 사용 팀에 귀속 */
    @Column(name = "assigned_team_id")
    private Long assignedTeamId;

    /** 소비 여부(한 번만 사용) */
    @Column(name = "consumed", nullable = false)
    private boolean consumed;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;
}
