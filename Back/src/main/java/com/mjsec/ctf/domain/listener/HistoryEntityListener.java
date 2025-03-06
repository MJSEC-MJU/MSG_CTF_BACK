package com.mjsec.ctf.domain.listener;

import com.mjsec.ctf.domain.ChallengeEntity;
import com.mjsec.ctf.domain.HistoryEntity;
import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.repository.ChallengeRepository;
import com.mjsec.ctf.repository.HistoryRepository;
import com.mjsec.ctf.repository.UserRepository;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.type.ErrorCode;
import com.mjsec.ctf.util.BeanUtils;
import jakarta.persistence.PostPersist;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class HistoryEntityListener {

    @PostPersist
    @Transactional
    public void afterInsert(HistoryEntity history) {

        UserRepository userRepository = BeanUtils.getBean(UserRepository.class);
        ChallengeRepository challengeRepository = BeanUtils.getBean(ChallengeRepository.class);
        HistoryRepository historyRepository = BeanUtils.getBean(HistoryRepository.class);

        List<String> userIds = historyRepository.findDistinctUserIds();

        for (String userId : userIds) {
            List<HistoryEntity> userHistoryList = historyRepository.findByUserId(userId);

            List<Long> challengeIds = userHistoryList.stream()
                    .map(HistoryEntity::getChallengeId)
                    .toList();

            int totalPoints = 0;
            for (Long challengeId : challengeIds) {
                ChallengeEntity challenge = challengeRepository.findById(challengeId)
                        .orElse(null);

                if (challenge != null) {
                    totalPoints += challenge.getPoints();
                }
            }

            UserEntity user = userRepository.findByLoginId(userId)
                    .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

            user.setTotalPoint(totalPoints);
            userRepository.save(user);
        }
    }
}

