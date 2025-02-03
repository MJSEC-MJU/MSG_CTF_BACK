package com.mjsec.ctf.entity;

import jakarta.persistence.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Slf4j
@Entity
public class Leaderboard {
    //제가 사용한 테이블명은 Leaderboard 로 했습니다.
    @Id
    private Long id; // Primary key
    private String userid;  // varchar(255)
    private String univ;
    private int totalPoint; // int

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUniv(){
        return univ;
    }
    public void setUniv(String univ) {
        this.univ = univ;
    }
    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserid() {
        return userid;
    }

    public void setUserid(String userid) {
        this.userid = userid;
    }

    public int getTotalPoint() {
        return totalPoint;
    }

    public void setTotalPoint(int totalPoint) {
        this.totalPoint = totalPoint;
    }
}
