package com.mjsec.ctf.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mjsec.ctf.type.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String univ;

    /* 처음엔 UserRole로 설정했으나 ERD 설계로 String 타입으로 변경
    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @Fetch(FetchMode.JOIN)
    private List<UserRole> role;
     */

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private int totalPoint;

    @Column(nullable = false)
    private int mileage;

    @JsonIgnore
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private LeaderboardEntity leaderboard;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SubmissionEntity> submissions;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RefreshEntity> refreshTokens;
}