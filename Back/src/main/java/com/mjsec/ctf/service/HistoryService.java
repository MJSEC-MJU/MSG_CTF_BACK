package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.ChallengeEntity;
import com.mjsec.ctf.domain.HistoryEntity;
import com.mjsec.ctf.dto.HistoryDto;
import com.mjsec.ctf.repository.ChallengeRepository;
import com.mjsec.ctf.repository.HistoryRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HistoryService {

    private final HistoryRepository historyRepository;
    private final ChallengeRepository challengeRepository;

    public HistoryService(HistoryRepository historyRepository, ChallengeRepository challengeRepository) {
        this.historyRepository = historyRepository;
        this.challengeRepository = challengeRepository;
    }

    /**
     * HistoryEntity들을 조회하여, 각 기록에 해당하는 ChallengeEntity의 최신 동적 점수(ChallengeEntity.points)를 HistoryDto에 반영합니다.
     * @return List of HistoryDto sorted by solvedTime ascending.
     */
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
                    history.getUserId(),
                    String.valueOf(history.getChallengeId()),
                    history.getSolvedTime(),
                    dynamicScore,
                    univ //univ 포함
            );
        }).collect(Collectors.toList());
    }
}
