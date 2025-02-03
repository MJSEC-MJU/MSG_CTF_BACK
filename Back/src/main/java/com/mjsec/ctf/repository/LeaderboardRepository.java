package com.mjsec.ctf.repository;

import com.mjsec.ctf.entity.Leaderboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaderboardRepository extends JpaRepository<Leaderboard, Long> {

    // 모든 데이터를 total_point 기준 내림차순으로 가져오기 (혼자 할때는 scroe 로 쓰다가 잘못넣었네요... ㅎㅎ)
    List<Leaderboard> findAllByOrderByTotalPointDescUpdatedAtAsc();

}
