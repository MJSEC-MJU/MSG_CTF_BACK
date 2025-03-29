package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.LeaderboardEntity;
import com.mjsec.ctf.repository.LeaderboardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LeaderboardService {

    private final LeaderboardRepository leaderboardRepository;

    @Autowired
    public LeaderboardService(LeaderboardRepository leaderboardRepository) {
        this.leaderboardRepository = leaderboardRepository;
    }

    // 모든 유저의 userid, total_poing, univ 데이터를 가져오는 메서드
    public List<LeaderboardEntity> getLeaderboard() {
        return leaderboardRepository.findAllByOrderByTotalPointDescLastSolvedTimeAsc(); // 모든 데이터를 total_point 기준 내림차순으로 가져오기
    }
}
