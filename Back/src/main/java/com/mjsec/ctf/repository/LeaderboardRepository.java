package com.mjsec.ctf.repository;

import com.mjsec.ctf.entity.Leaderboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaderboardRepository extends JpaRepository<Leaderboard, Long> {

    // 정렬 방법 변경했습니다
    List<Leaderboard> findAllByOrderByTotalPointDescLastSolvedTimeAsc();
    
    // 특정 회원의 Leaderboard 정보를 조회하는 메서드 추가
    Optional<Leaderboard> findByUserid(String userid);
}