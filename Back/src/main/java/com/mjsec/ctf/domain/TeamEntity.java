package com.mjsec.ctf.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.Builder;              // @Builder.Default 사용을 위해 필요
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
@Table(name = "team") // @SQLDelete의 테이블명과 일치시킴
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
    private int mileage;

    @Column(name = "total_point", nullable = false)
    private int totalPoint;

    @Type(JsonType.class)
    @Column(name = "member_user_ids", columnDefinition = "json")
    @Builder.Default
    private List<Long> memberUserIds = new ArrayList<>();

    @Type(JsonType.class)
    @Column(name = "solved_challenge_ids", columnDefinition = "json")
    @Builder.Default
    private List<Long> solvedChallengeIds = new ArrayList<>();

    @Column(name = "last_solved_time")
    private LocalDateTime lastSolvedTime;

    @PrePersist
    @PreUpdate
    public void ensureNonNullCollections() {
        if (memberUserIds == null) {
            memberUserIds = new ArrayList<>();
        }
        if (solvedChallengeIds == null) {
            solvedChallengeIds = new ArrayList<>();
        }
    }

    public List<Long> getMemberUserIds() {
        if (memberUserIds == null) {
            memberUserIds = new ArrayList<>();
        }
        return memberUserIds;
    }

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


    public boolean addSolvedChallenge(Long challengeId) {
        if (!solvedChallengeIds.contains(challengeId)) {
            solvedChallengeIds.add(challengeId);
            this.lastSolvedTime = LocalDateTime.now();
            return true;
        }
        return false;
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

    // 문제 풀이 철회 (점수, 마일리지 반환)
    public void revokeSolvedChallenge(Long challengeId, int points, int mileage) {
        if (solvedChallengeIds.contains(challengeId)) {
            solvedChallengeIds.remove(challengeId);
            this.totalPoint = Math.max(0, this.totalPoint - points);
            this.mileage = Math.max(0, this.mileage - mileage);
        }
    }
}
