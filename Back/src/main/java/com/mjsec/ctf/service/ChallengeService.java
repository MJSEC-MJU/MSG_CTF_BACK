package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.ChallengeEntity;
import com.mjsec.ctf.domain.HistoryEntity;
import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.dto.ChallengeDto;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.repository.ChallengeRepository;
import com.mjsec.ctf.repository.HistoryRepository;
import com.mjsec.ctf.repository.UserRepository;
import com.mjsec.ctf.type.ErrorCode;
import io.micrometer.common.util.StringUtils;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;


@Slf4j
@RequiredArgsConstructor
@Service
public class ChallengeService {

    private final FileService fileService;
    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final HistoryRepository historyRepository;

    @Value("${api.key}")
    private String apiKey;

    @Value("${api.url}")
    private String apiUrl;
    
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
    public void updateChallenge(Long challengeId, MultipartFile file, ChallengeDto challengeDto) throws IOException {

        ChallengeEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));

        if(challengeDto != null) {
            challenge = ChallengeEntity.builder()
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
        }

        if(file != null) {
            String fileUrl = fileService.store(file);
            challenge.setFileUrl(fileUrl);
        }

        challengeRepository.save(challenge);
    }

    // 문제 삭제
    public void deleteChallenge(Long challengeId){

        ChallengeEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));

        challengeRepository.delete(challenge);
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

    // 문제(플래그) 제출
    @Transactional
    public String submit(String loginId, Long challengeId, String flag) {

        if (flag == null || StringUtils.isBlank(flag)) {
            return "Flag cannot be null or empty";
        }

        UserEntity user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

        ChallengeEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));

        if(!Objects.equals(flag, challenge.getFlag())){
            return "Wrong";
        } else {
            if(historyRepository.existsByUserIdAndChallengeId(user.getLoginId(), challengeId)){
                return "Already submitted";
            }

            HistoryEntity history = HistoryEntity.builder()
                    .userId(user.getLoginId())
                    .challengeId(challenge.getChallengeId())
                    .solvedTime(LocalDateTime.now())
                    .build();

            historyRepository.save(history);

            long solvedCount = historyRepository.countDistinctByChallengeId(challengeId);
            if (solvedCount == 1) {
                sendFirstBloodNotification(challenge, user);
            }

            updateChallengeScore(challenge);

            return "Correct";
        }
    }

    // 문제 점수 계산기
    public void updateChallengeScore(ChallengeEntity challenge) {

        // 현재 챌린지를 해결한 사용자 수를 계산
        long solvedCount = historyRepository.countDistinctByChallengeId(challenge.getChallengeId());

        // 전체 참가자 수를 계산
        long totalParticipants = userRepository.count();

        double maxDecrementFactor = 0.9;

        double newPoints;
        if (totalParticipants > 1) {
            // 점수 감소 비율 계산
            double decrementFactor = maxDecrementFactor * (solvedCount - 1) / (totalParticipants - 1);
            decrementFactor = Math.min(decrementFactor, maxDecrementFactor);

            // 초기 점수에서 점수를 감소시킴
            newPoints = challenge.getInitialPoints() * (1 - decrementFactor);
            // 최소 점수 이하로 떨어지지 않도록 함
            newPoints = Math.max(newPoints, challenge.getMinPoints());
        } else {
            newPoints = challenge.getInitialPoints();  // 참가자가 1명 이하인 경우 초기 점수 유지
        }

        newPoints = Math.floor(newPoints);
        challenge.setPoints((int)newPoints);

        challengeRepository.save(challenge);
    }

    // 퍼스트 블러드 Sender
    private void sendFirstBloodNotification(ChallengeEntity challenge, UserEntity user) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-API-Key", apiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("first_blood_problem", challenge.getTitle());
        body.put("first_blood_person", user.getLoginId());
        body.put("first_blood_school", user.getUniv());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("First blood notification sent successfully.");
        } else {
            log.error("Failed to send first blood notification.");
        }
    }
}