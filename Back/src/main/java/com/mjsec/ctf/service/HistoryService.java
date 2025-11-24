package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.ChallengeEntity;
import com.mjsec.ctf.domain.TeamHistoryEntity;
import com.mjsec.ctf.dto.TeamHistoryDto;
import com.mjsec.ctf.repository.ChallengeRepository;
import com.mjsec.ctf.repository.TeamHistoryRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HistoryService {

    private final TeamHistoryRepository teamHistoryRepository;
    private final ChallengeRepository challengeRepository;

    public HistoryService(TeamHistoryRepository teamHistoryRepository, ChallengeRepository challengeRepository) {
        this.teamHistoryRepository = teamHistoryRepository;
        this.challengeRepository = challengeRepository;
    }

    /**
     * HistoryEntity들을 조회하여, 각 기록에 해당하는 ChallengeEntity의 최신 동적 점수(ChallengeEntity.points)를 HistoryDto에 반영합니다.
     * @return List of HistoryDto sorted by solvedTime ascending.
     */
    /*
    getHistoryDtos -> 전체 멤버 (삭제 유저 포함) 조회로 수정.
    실질적인 삭제 안 된 유저는 getActiveUserHistoryDtos 로 바꿈.

    public List<HistoryDto> getHistoryDtos() {
        List<HistoryEntity> histories = historyRepository.findAllByOrderBySolvedTimeAsc();
        return histories.stream().map(history -> {
            ChallengeEntity challenge = challengeRepository.findById(history.getChallengeId())
                    .orElse(null);
            int dynamicScore = (challenge != null) ? challenge.getPoints() : 0;

            //univ 추가
            String univ = history.getUniv(); //엔티티에서 메서드 호출

            // HistoryDto의 challengeId 타입이 String인 경우 문자열로 변환합니다.
            return new HistoryDto(
                    history.getLoginId(),
                    String.valueOf(history.getChallengeId()),
                    challenge.getTitle(),
                    history.getSolvedTime(),
                    dynamicScore,
                    univ //univ 포함
            );
        }).collect(Collectors.toList());
    }
     */


    public List<TeamHistoryDto> getActiveUserHistoryDtos() {
        List<TeamHistoryEntity> histories = teamHistoryRepository.findAllByOrderBySolvedTimeAsc();

        return histories.stream().map(history -> {
            ChallengeEntity challenge = challengeRepository.findById(history.getChallengeId())
                    .orElse(null);
            int dynamicScore = (challenge != null) ? challenge.getPoints() : 0;

            return new TeamHistoryDto(
                    history.getHistoryid(),
                    history.getTeamName(),
                    String.valueOf(history.getChallengeId()),
                    challenge != null ? challenge.getTitle() : "Unknown Challenge",
                    history.getSolvedTime(),
                    dynamicScore
            );
        }).collect(Collectors.toList());
    }
}
