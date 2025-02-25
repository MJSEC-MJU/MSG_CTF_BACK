package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.ChallengeEntity;
import com.mjsec.ctf.dto.ChallengeDto;
import com.mjsec.ctf.repository.ChallengeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;


@Slf4j
@RequiredArgsConstructor
@Service
public class ChallengeService {

    private  final ChallengeRepository challengeRepository;
    
    //모든 문제 조회
    public Page<ChallengeDto.Simple> getAllChallenges(Pageable pageable) {
        log.info("Getting all challenges!!");

        Page<ChallengeEntity> challenges = challengeRepository.findAllChallenges(pageable);

        return challenges.map(ChallengeDto.Simple::fromEntity);
    }
}
