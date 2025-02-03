package com.mjsec.ctf.service;

import com.mjsec.ctf.entity.Leaderboard;
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
    public List<Leaderboard> getLeaderboard() {
        return leaderboardRepository.findAllByOrderByTotalPointDescUpdatedAtAsc(); // 모든 데이터를 score 기준 내림차순으로 가져오기
    }

}
