package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.ChallengeEntity;
import com.mjsec.ctf.dto.ChallengeDto;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.repository.ChallengeRepository;
import com.mjsec.ctf.type.ErrorCode;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


@Slf4j
@RequiredArgsConstructor
@Service
public class ChallengeService {

    private final FileService fileService;
    private final ChallengeRepository challengeRepository;
    
    //모든 문제 조회
    public Page<ChallengeDto.Simple> getAllChallengesOrderedById(Pageable pageable) {
        log.info("Getting all challenges ordered by Id ASC!!");

        Page<ChallengeEntity> challenges = challengeRepository.findAllByOrderByChallengeIdAsc(pageable);

        return challenges.map(ChallengeDto.Simple::fromEntity);
    }

    //특정 문제 상세 조회 (문제 설명, 문제 id, point)
    public ChallengeDto.Detail getDetailChallenge(Long challengeId){
        log.info("Fetching details for challengeId: {}", challengeId);

        // 해당 challengeId를 가진 엔티티 조회
        ChallengeEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));

        return ChallengeDto.Detail.fromEntity(challenge);
    }

    // 문제 생성
    public void createChallenge(MultipartFile file, ChallengeDto challengeDto) throws IOException {

        if(challengeDto == null) {
            throw new RestApiException(ErrorCode.REQUIRED_FIELD_NULL);
        }

        ChallengeEntity challenge = ChallengeEntity.builder()
                .title(challengeDto.getTitle())
                .description(challengeDto.getDescription())
                .flag(challengeDto.getFlag())
                .points(challengeDto.getPoints())
                .minPoints(challengeDto.getMinPoints())
                .initialPoints(challengeDto.getInitialPoints())
                .startTime(challengeDto.getStartTime())
                .endTime(challengeDto.getEndTime())
                .url(challengeDto.getUrl())
                .build();

        if(file != null) {
            String fileUrl = fileService.store(file);
            challenge.setFileUrl(fileUrl);
        }

        challengeRepository.save(challenge);
    }

    // 문제 수정
    public void updateChallenge(MultipartFile file, ChallengeDto challengeDto) throws IOException {

        if(challengeDto == null) {
            throw new RestApiException(ErrorCode.REQUIRED_FIELD_NULL);
        }

        ChallengeEntity challenge = ChallengeEntity.builder()
                .title(challengeDto.getTitle())
                .description(challengeDto.getDescription())
                .flag(challengeDto.getFlag())
                .points(challengeDto.getPoints())
                .minPoints(challengeDto.getMinPoints())
                .initialPoints(challengeDto.getInitialPoints())
                .startTime(challengeDto.getStartTime())
                .endTime(challengeDto.getEndTime())
                .url(challengeDto.getUrl())
                .build();

        if(file != null) {
            String fileUrl = fileService.store(file);
            challenge.setFileUrl(fileUrl);
        }

        challengeRepository.save(challenge);
    }
    // 문제 파일 다운로드
    public byte[] downloadChallengeFile(Long challengeId) throws IOException {
    // 해당 challengeId로 ChallengeEntity를 조회합니다.
    ChallengeEntity challenge = challengeRepository.findById(challengeId)
            .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));
    
    // 파일 URL이 없으면 예외 처리
    if (challenge.getFileUrl() == null) {
        throw new RestApiException(ErrorCode.FILE_NOT_FOUND);
    }
    String fileUrl = challenge.getFileUrl();
    String fileId = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
    
    return fileService.download(fileId);

}
