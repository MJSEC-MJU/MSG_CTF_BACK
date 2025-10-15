package com.mjsec.ctf.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@SuperBuilder
@Table(
    name = "challenge_signature_policy",
    uniqueConstraints = @UniqueConstraint(name = "uk_policy_challenge", columnNames = "challenge_id")
)
@SQLDelete(sql = "UPDATE challenge_signature_policy SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at is null")
public class ChallengeSignaturePolicy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 1:1 (문제별 정책)
    @Column(name = "challenge_id", nullable = false, unique = true)
    private Long challengeId;

    // 운영 비교 키 (SIGNATURE 전용)
    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String club;

    @Column(nullable = false, length = 255)
    private String signature; // 필요시 해시로 변경 가능
}
