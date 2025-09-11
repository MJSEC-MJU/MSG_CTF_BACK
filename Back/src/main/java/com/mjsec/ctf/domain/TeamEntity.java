package com.mjsec.ctf.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonType;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Inheritance(strategy = InheritanceType.JOINED)
@SQLDelete(sql = "UPDATE team SET deleted_at = NOW() WHERE team_id = ?")
@SQLRestriction("deleted_at is null")
public class TeamEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "team_name", nullable = false, unique = true, length = 50)
    private String teamName;

    @Column(name = "mileage", nullable = false)
    private int mileage = 0;

    @Column(name = "total_point", nullable = false)
    private int totalPoint = 0;

    @Column(name = "max_members", nullable = false)
    private int maxMembers = 4;

    @Type(JsonType.class)
    @Column(name = "member_user_ids", columnDefinition = "json")
    private List<Long> memberUserIds = new ArrayList<>();

    @Type(JsonType.class)
    @Column(name = "solved_challenge_ids", columnDefinition = "json")
    private List<Long> solvedChallengeIds = new ArrayList<>();

    @Column(name = "last_solved_time")
    private LocalDateTime lastSolvedTime;

    public boolean isMember(Long userId) {

        return memberUserIds.contains(userId);
    }

    public void addMember(Long userId) {

        if (!memberUserIds.contains(userId)) {
            memberUserIds.add(userId);
        }
    }

    public void removeMember(Long userId) {

        memberUserIds.remove(userId);
    }

    public boolean hasSolvedChallenge(Long challengeId) {

        return solvedChallengeIds.contains(challengeId);
    }

    public void addSolvedChallenge(Long challengeId, int points) {

        if (!solvedChallengeIds.contains(challengeId)) {
            solvedChallengeIds.add(challengeId);
            this.totalPoint += points;
            this.lastSolvedTime = LocalDateTime.now();
        }
    }

    public void addMileage(int amount) {

        this.mileage += amount;
    }

    public boolean deductMileage(int amount) {

        if (this.mileage >= amount) {
            this.mileage -= amount;
            return true;
        }
        return false;
    }

    public int getSolvedCount() {

        return solvedChallengeIds.size();
    }
}
