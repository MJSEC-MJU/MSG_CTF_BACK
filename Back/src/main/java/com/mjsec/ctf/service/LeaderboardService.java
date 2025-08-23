package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.LeaderboardEntity;
import com.mjsec.ctf.repository.LeaderboardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LeaderboardService {

    private final LeaderboardRepository leaderboardRepository;

    @Autowired
    public LeaderboardService(LeaderboardRepository leaderboardRepository) {
        this.leaderboardRepository = leaderboardRepository;
    }

    // 모든 유저의 userid, total_poing, univ 데이터를 가져오는 메서드
    public List<LeaderboardEntity> getLeaderboard() {
        return leaderboardRepository.findAllByOrderByTotalPointDescLastSolvedTimeAsc()
                .stream()
                .filter(leaderboard -> leaderboard.getTotalPoint() > 0) // 0점 유저 제외
                .collect(Collectors.toList());
    }
    // 여기서 한 부분이 달라졌는데 본래 updatedAt 으로 정렬하던것을 LastSolvedTime 으로 정렬하게 바꿨습니다
    // 이때 lastSolvedTime 은 mysql 에서 트리거를 활용하여 설정됩니다.
    // 문제별로 테이블을 제작합니다 ex) problem_1 or problem_2
    // 각 테이블 별로 컬럼을 설정합니다 ex) userId (문제를 풀이한 사람이 저장됩니다.), solved_at
    // 트리거를 활용 solved_at 의 값을 leaderboard 테이블 내의 last_solved_at 에 저장합니다.
    // 이후 해당 값을 활용하면 최종적으로 문제를 마지막에 풀이한 사람이 더 낮은 등수를 갖게 됩니다. (테스트 완료)

}
