package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.TeamEntity;
import com.mjsec.ctf.dto.TeamLeaderboardDto;    //팀단위 추가
import com.mjsec.ctf.repository.TeamRepository; //TeamRepository참조
//import com.mjsec.ctf.domain.LeaderboardEntity;    //개인용은 주석처리
//import com.mjsec.ctf.repository.LeaderboardRepository;    //개인용 주석처리
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class LeaderboardService {

    private final TeamRepository teamRepository;    //leaderboardRepository에서 Team으로 변경

    @Autowired
    public LeaderboardService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    // 팀기반 리더보드 조회 (점수 내림차순)
    public List<TeamLeaderboardDto> getTeamLeaderboard() {
        List<TeamEntity> teams = teamRepository.findAllByOrderByTotalPointDescLastSolvedTimeAsc()
                .stream()
                .filter(team -> team.getTotalPoint() > 0)   //0점 팀 제외
                .collect(Collectors.toList());

        AtomicInteger rank = new AtomicInteger(1);  //순위계산 (1등부터)

        return teams.stream()
                .map(team -> TeamLeaderboardDto.builder()
                        .teamId(team.getTeamId())
                        .teamName(team.getTeamName())
                        .totalPoint(team.getTotalPoint())
                        .solvedCount(team.getSolvedCount())
                        .lastSolvedTime(team.getLastSolvedTime())
                        .rank(rank.getAndIncrement())  // 순위 자동 증가
                        .build())
                .collect(Collectors.toList());
    }
    // 여기서 한 부분이 달라졌는데 본래 updatedAt 으로 정렬하던것을 LastSolvedTime 으로 정렬하게 바꿨습니다
    // 이때 lastSolvedTime 은 mysql 에서 트리거를 활용하여 설정됩니다.
    // 문제별로 테이블을 제작합니다 ex) problem_1 or problem_2
    // 각 테이블 별로 컬럼을 설정합니다 ex) userId (문제를 풀이한 사람이 저장됩니다.), solved_at
    // 트리거를 활용 solved_at 의 값을 leaderboard 테이블 내의 last_solved_at 에 저장합니다.
    // 이후 해당 값을 활용하면 최종적으로 문제를 마지막에 풀이한 사람이 더 낮은 등수를 갖게 됩니다. (테스트 완료)

    //수정부분에서 MySQL트리거 leaderboard_entity받는다면 team_entity로 교체해야됨.
}
