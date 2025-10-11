//개인용이므로 지금현재는 주석처리해도 상관없음.
package com.mjsec.ctf.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Slf4j
@Entity
@Getter
@Setter
public class LeaderboardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // id 자동 증가
    @Column(name = "id")  // DB 컬럼명 매칭
    private Long id;

    @Column(name = "loginId") // 컬럼 매핑 <-loginId 로 수정
    private String loginId;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loginId", referencedColumnName = "loginId", insertable=false, updatable=false)
    private UserEntity user;

    @Column(name = "total_point")  // total_point 컬럼 매핑
    private int totalPoint;

    @Column(name = "last_solved_time")  // last_solved_time 컬럼 매핑
    private LocalDateTime lastSolvedTime;

    @Column(name = "univ")
    private String univ;

}