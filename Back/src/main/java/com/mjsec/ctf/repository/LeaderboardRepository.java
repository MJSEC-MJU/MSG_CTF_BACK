package com.mjsec.ctf.repository;

import com.mjsec.ctf.entity.Leaderboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaderboardRepository extends JpaRepository<Leaderboard, Long> {

    // 모든 데이터를 score 기준 내림차순으로 가져오기
    List<Leaderboard> findAllByOrderByTotalPointDesc();

}
