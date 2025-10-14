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
@Table(
    name = "team_signature_unlock",
    uniqueConstraints = @UniqueConstraint(name = "uk_unlock_team_challenge", columnNames = {"team_id","challenge_id"})
)
@SQLDelete(sql = "UPDATE team_signature_unlock SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at is null")
public class TeamSignatureUnlockEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "challenge_id", nullable = false)
    private Long challengeId;

    @Column(nullable = false, length = 255)
    private String signature;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String club;

    @Column(nullable = false)
    private LocalDateTime unlockedAt;
}
