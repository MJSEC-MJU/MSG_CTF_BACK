package com.mjsec.ctf.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String loginId;

    @Column
    private Long challengeId;

    @Column
    private int attemptCount;

    @Column
    private LocalDateTime lastAttemptTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loginId", referencedColumnName = "loginId", insertable=false, updatable=false)
    private UserEntity user;
}
