package com.mjsec.ctf.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Slf4j
@Entity
@Getter
@Setter
@Table(name = "leaderboard") 
public class Leaderboard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id")
    private String userid;

    @Column(name = "total_point")
    private int totalPoint;

    @Column(name = "last_solved_time")
    private LocalDateTime lastSolvedTime;

    @Column(name = "univ")
    private String univ;

}