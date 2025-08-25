package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.ChallengeEntity;
import com.mjsec.ctf.domain.HistoryEntity;
import com.mjsec.ctf.domain.SubmissionEntity;
import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.dto.ChallengeDto;
import com.mjsec.ctf.domain.LeaderboardEntity;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.repository.ChallengeRepository;
import com.mjsec.ctf.repository.HistoryRepository;
import com.mjsec.ctf.repository.SubmissionRepository;
import com.mjsec.ctf.repository.UserRepository;
import com.mjsec.ctf.repository.LeaderboardRepository;
import com.mjsec.ctf.type.ErrorCode;
import io.micrometer.common.util.StringUtils;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final LeaderboardRepository leaderboardRepository;
    private final SubmissionRepository submissionRepository;

    /*
    7월 30일자 테스트할 땐
    BcryptPasswordEncoder -> PasswordEncoder로 변경해서 진행했음.
    (혹시 몰라 메모해둠)
     */
    private final BCryptPasswordEncoder passwordEncoder;
    private final RedissonClient redissonClient;

    @Value("${api.key}")
    private String apiKey;

    @Value("${api.url}")
    private String apiUrl;

    // 현재 사용자 ID를 반환
    public String currentUserId(){

        log.info("Getting user id from security context holder");
        String loginId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        log.info("Successfully returned login id from security context holder : {}", loginId);

        return loginId;
    }
    
    // 모든 문제 조회 (ID 오름차순)
    public Page<ChallengeDto.Simple> getAllChallengesOrderedById(Pageable pageable) {
        log.info("Getting all challenges ordered by Id ASC!!");

        Page<ChallengeEntity> challenges = challengeRepository.findAllByOrderByChallengeIdAsc(pageable);

        return challenges.map(challenge -> {
            boolean solved = historyRepository.existsByLoginIdAndChallengeId(currentUserId(), challenge.getChallengeId());
            return ChallengeDto.Simple.fromEntity(challenge, solved);
        });
    }

    // 특정 문제 상세 조회 (문제 설명, 문제 id, point 등)
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
        
        // 빌더 객체 생성
        ChallengeEntity.ChallengeEntityBuilder builder = ChallengeEntity.builder()
                .title(challengeDto.getTitle())
                .description(challengeDto.getDescription())
                .flag(passwordEncoder.encode(challengeDto.getFlag()))
                .points(challengeDto.getPoints())
                .minPoints(challengeDto.getMinPoints())
                .initialPoints(challengeDto.getInitialPoints())
                .startTime(challengeDto.getStartTime())
                .endTime(challengeDto.getEndTime())
                .url(challengeDto.getUrl());

        // 카테고리 설정: challengeDto.getCategory()가 제공된 경우 처리
        if (challengeDto.getCategory() != null && !challengeDto.getCategory().isBlank()) {
            try {
                builder.category(com.mjsec.ctf.type.ChallengeCategory.valueOf(challengeDto.getCategory().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new RestApiException(ErrorCode.BAD_REQUEST, "유효하지 않은 카테고리입니다.");
            }
        } else {
            // 기본값 설정 (예: MISC)
            builder.category(com.mjsec.ctf.type.ChallengeCategory.MISC);
        }
        
        ChallengeEntity challenge = builder.build();
    
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
            // 새 빌더를 이용해 수정된 엔티티 생성 (ID는 유지)
            ChallengeEntity updatedChallenge = ChallengeEntity.builder()
                    .challengeId(challenge.getChallengeId())
                    .title(challengeDto.getTitle())
                    .description(challengeDto.getDescription())
                    .flag(passwordEncoder.encode(challengeDto.getFlag()))
                    .points(challengeDto.getPoints())
                    .minPoints(challengeDto.getMinPoints())
                    .initialPoints(challengeDto.getInitialPoints())
                    .startTime(challengeDto.getStartTime())
                    .endTime(challengeDto.getEndTime())
                    .url(challengeDto.getUrl())
                    .build();
            // 카테고리 설정
            if (challengeDto.getCategory() != null && !challengeDto.getCategory().isBlank()) {
                try {
                    updatedChallenge.setCategory(com.mjsec.ctf.type.ChallengeCategory.valueOf(challengeDto.getCategory().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    throw new RestApiException(ErrorCode.BAD_REQUEST, "유효하지 않은 카테고리입니다.");
                }
            } else {
                updatedChallenge.setCategory(challenge.getCategory());
            }
    
            // 기존 파일 URL 유지
            updatedChallenge.setFileUrl(challenge.getFileUrl());
            challenge = updatedChallenge;
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
        // 해당 challenge_id에 해당하는 history 레코드를 먼저 삭제
        historyRepository.deleteByChallengeId(challengeId);
        
        challengeRepository.delete(challenge);

        updateTotalPoints();
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

    @Transactional
    public String submit(String loginId, Long challengeId, String flag) {

        String lockKey = "challengeLock:" + challengeId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;

        try {
            locked = lock.tryLock(10, 10, TimeUnit.SECONDS);
            if (!locked) {
                return "Try again later";
            }

            if (flag == null || StringUtils.isBlank(flag)) {
                return "Flag cannot be null or empty";
            }

            UserEntity user = userRepository.findByLoginId(loginId)
                    .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

            ChallengeEntity challenge = challengeRepository.findById(challengeId)
                    .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));

            if (historyRepository.findWithLockByLoginIdAndChallengeId(user.getLoginId(), challengeId).isPresent()) {
                return "Submitted";
            }

            SubmissionEntity submission = submissionRepository.findByLoginIdAndChallengeId(loginId, challengeId)
                    .orElseGet(() -> SubmissionEntity.builder()
                            .loginId(loginId)
                            .challengeId(challengeId)
                            .attemptCount(0)
                            .lastAttemptTime(LocalDateTime.now())
                            .build());

            long secondsSinceLastAttempt = ChronoUnit.SECONDS.between(submission.getLastAttemptTime(), LocalDateTime.now());
            if (submission.getAttemptCount() > 2 && secondsSinceLastAttempt < 30) {
                return "Wait";
            }

            if (!passwordEncoder.matches(flag, challenge.getFlag())) {
                submission.setAttemptCount(submission.getAttemptCount() + 1);
                submission.setLastAttemptTime(LocalDateTime.now());
                submissionRepository.save(submission);
                return "Wrong";
            } else {
                HistoryEntity history = HistoryEntity.builder()
                        .loginId(user.getLoginId())
                        .challengeId(challenge.getChallengeId())
                        .solvedTime(LocalDateTime.now())
                        .univ(user.getUniv())
                        .build();
                historyRepository.save(history);

                // firstBloodLock 안전하게 처리
                String firstBloodLockKey = "firstBloodLock:" + challengeId;
                RLock firstBloodLock = redissonClient.getLock(firstBloodLockKey);
                boolean firstBloodLocked = false;

                try {
                    firstBloodLocked = firstBloodLock.tryLock(5, 5, TimeUnit.SECONDS);
                    if (firstBloodLocked) {
                        long solvedCount = historyRepository.countDistinctByChallengeId(challengeId);
                        if (solvedCount == 1) {
                            sendFirstBloodNotification(challenge, user);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    if (firstBloodLocked && firstBloodLock.isHeldByCurrentThread()) {
                        firstBloodLock.unlock();
                    }
                }

                user.setMileage(user.getMileage() + 100); // 제출 정답 시 마일리지 부여
                userRepository.save(user);

                updateChallengeScore(challenge);
                challenge.setSolvers(challenge.getSolvers() + 1);
                challengeRepository.save(challenge);
                updateTotalPoints();
                submissionRepository.delete(submission);

                return "Correct";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error while processing";
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // Leaderboard 업데이트 메서드
    private void updateLeaderboard(UserEntity user, LocalDateTime solvedTime) {
        // 이미 존재하는 Leaderboard 레코드를 조회
        var optionalLeaderboard = leaderboardRepository.findByLoginId(user.getLoginId());
        LeaderboardEntity leaderboardEntity;
        if (optionalLeaderboard.isPresent()) {
            leaderboardEntity = optionalLeaderboard.get();
        } else {
            leaderboardEntity = new LeaderboardEntity();
            leaderboardEntity.setLoginId(user.getLoginId());
        }
        
        // 사용자의 TotalPoint 와 LastSolvedTIme, Univ
        leaderboardEntity.setTotalPoint(user.getTotalPoint());
        leaderboardEntity.setLastSolvedTime(solvedTime);
        leaderboardEntity.setUniv(user.getUniv());
        
        leaderboardRepository.save(leaderboardEntity);
    }

    // 문제 점수 계산기 (updateChallengeScore 메서드 수정 - 삭제된 사용자 제외)
    public void updateChallengeScore(ChallengeEntity challenge) {

        long solvedCount = historyRepository.countDistinctByChallengeId(challenge.getChallengeId());

        int initialPoints = challenge.getInitialPoints();
        int minPoints = challenge.getMinPoints();
        int decay = 50;

        double newPoints = (((double)(minPoints - initialPoints) / (decay * decay)) * (solvedCount * solvedCount)) + initialPoints;

        newPoints = Math.max(newPoints, minPoints);

        newPoints = Math.ceil(newPoints);
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

    public void updateTotalPoints () {

        List<String> loginIds = historyRepository.findDistinctLoginIds();

        if(loginIds.isEmpty()){
            List<String> userLoginIds = userRepository.findAllUserLoginIds();

            for(String loginId : userLoginIds){
                UserEntity user = userRepository.findByLoginId(loginId)
                        .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

                user.setTotalPoint(0);
                userRepository.save(user);

                LeaderboardEntity leaderboardEntity = leaderboardRepository.findByLoginId(user.getLoginId())
                        .orElseThrow(() -> new RestApiException(ErrorCode.LEADERBOARD_NOT_FOUND));

                leaderboardEntity.setTotalPoint(0);
                leaderboardEntity.setLastSolvedTime(null);
                leaderboardRepository.save(leaderboardEntity);
            }

            return;
        }

        for (String loginId : loginIds) {
            List<HistoryEntity> userHistoryList = historyRepository.findByLoginIdAndUserDeletedFalse(loginId);

            List<Long> challengeIds = userHistoryList.stream()
                    .map(HistoryEntity::getChallengeId)
                    .toList();

            LocalDateTime lastSolvedTime = null;
            for (HistoryEntity userHistory : userHistoryList) {
                if (lastSolvedTime == null || userHistory.getSolvedTime().isAfter(lastSolvedTime)) {
                    lastSolvedTime = userHistory.getSolvedTime();
                }
            }

            int totalPoints = 0;
            for (Long challengeId : challengeIds) {
                ChallengeEntity challenge = challengeRepository.findById(challengeId)
                        .orElse(null);

                if (challenge != null) {
                    totalPoints += challenge.getPoints();
                }
            }

            UserEntity user = userRepository.findByLoginId(loginId)
                    .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

            user.setTotalPoint(totalPoints);
            userRepository.save(user);

            updateLeaderboard(user, lastSolvedTime);
        }
    }
}
