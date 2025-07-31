package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.LeaderboardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface LeaderboardRepository extends JpaRepository<LeaderboardEntity, Long> {

    // 정렬 방법 변경했습니다
    List<LeaderboardEntity> findAllByOrderByTotalPointDescLastSolvedTimeAsc();
    
    // 특정 회원의 Leaderboard 정보를 조회하는 메서드 추가
    Optional<LeaderboardEntity> findByLoginId(String loginId);
}