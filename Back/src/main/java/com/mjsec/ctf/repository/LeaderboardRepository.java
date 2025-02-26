package com.mjsec.ctf.repository;

import com.mjsec.ctf.entity.Leaderboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaderboardRepository extends JpaRepository<Leaderboard, Long> {

    // 정렬 방법 변경했습니다
    List<Leaderboard> findAllByOrderByTotalPointDescLastSolvedTimeAsc();
}