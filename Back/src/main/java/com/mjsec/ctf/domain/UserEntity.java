package com.mjsec.ctf.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mjsec.ctf.type.UserRole;
import jakarta.persistence.*;
import lombok.*;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false)
    private int totalPoint;

    @Column
    private Long currentTeamId;

    @JsonIgnore
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private LeaderboardEntity leaderboard;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SubmissionEntity> submissions;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RefreshEntity> refreshTokens;

    @Column(nullable = false)
    @Builder.Default
    private boolean earlyExit = false;

    public boolean hasTeam() {

        return currentTeamId != null;
    }

    public void joinTeam(Long teamId) {

        this.currentTeamId = teamId;
    }

    public void leaveTeam() {

        this.currentTeamId = null;
    }

    public void markEarlyExit() {
        this.earlyExit = true;
    }

    public void cancelEarlyExit() {
        this.earlyExit = false;
    }
}
