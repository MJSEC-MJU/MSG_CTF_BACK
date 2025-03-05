package com.mjsec.ctf.domain;

import com.mjsec.ctf.type.ChallengeCategory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
@Inheritance(strategy = InheritanceType.JOINED)
@SQLDelete(sql = "UPDATE challenge_entity SET deleted_at = NOW() WHERE challenge_id = ?")
@SQLRestriction("deleted_at is null")
public class ChallengeEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long challengeId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String flag;

    @Column(nullable = false)
    private int points;

    @Column(nullable = false)
    private int minPoints;

    @Column(nullable = false)
    private int initialPoints;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Column
    private String fileUrl;

    @Column
    private String url;

    @Column(nullable = false)
    @Builder.Default
    private int solvers = 0;

    // 문제 카테고리 추가 (enum 타입)
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ChallengeCategory category;
}
