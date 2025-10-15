package com.mjsec.ctf.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Table(
    name = "team_signature_unlock",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_unlock_team_challenge",
            columnNames = {"team_id", "challenge_id"}
        )
    },
    indexes = {
        @Index(name = "idx_unlock_team", columnList = "team_id"),
        @Index(name = "idx_unlock_challenge", columnList = "challenge_id")
    }
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

    @Column(name = "unlocked_at", nullable = false)
    private LocalDateTime unlockedAt;
}
