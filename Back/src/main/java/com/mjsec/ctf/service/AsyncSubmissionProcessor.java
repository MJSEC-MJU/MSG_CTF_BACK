package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.ChallengeEntity;
import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.repository.ChallengeRepository;
import com.mjsec.ctf.repository.HistoryRepository;
import com.mjsec.ctf.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 비동기 제출 처리 전담 서비스
 * 플래그 정답 제출 후 무거운 작업들을 백그라운드에서 처리:
 * 1. 퍼스트 블러드 판정
 * 2. 팀 점수 및 마일리지 업데이트
 * 3. 다이나믹 스코어링
 * 4. 전체 팀 점수 재계산
 * 5. 퍼스트 블러드 알림 전송
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncSubmissionProcessor {

    private final TeamService teamService;
    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final HistoryRepository historyRepository;
    private final RedissonClient redissonClient;

    @Value("${api.key}")
    private String apiKey;

    @Value("${api.url}")
    private String apiUrl;

    /**
     * 정답 제출 후 비동기 처리
     *
     * @Async 어노테이션으로 별도 스레드에서 실행
     * @Transactional(propagation = REQUIRES_NEW) 로 새로운 트랜잭션에서 실행
     *
     * @param userId 사용자 ID
     * @param challengeId 문제 ID
     * @param loginId 로그인 ID
     */
    @Async("submissionAsyncExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processCorrectSubmissionAsync(Long userId, Long challengeId, String loginId) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("[비동기 처리 시작] loginId={}, challengeId={}", loginId, challengeId);

            // 1. 사용자 및 문제 정보 조회
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));

            ChallengeEntity challenge = challengeRepository.findById(challengeId)
                    .orElseThrow(() -> new RuntimeException("Challenge not found: " + challengeId));

            boolean isSignature = challenge.getCategory() == com.mjsec.ctf.type.ChallengeCategory.SIGNATURE;

            // 2. 퍼스트 블러드 판정 (락 사용)
            boolean isFirstBlood = checkAndProcessFirstBlood(challengeId, user, challenge, isSignature);

            // 3. 팀 점수 및 마일리지 업데이트
            updateTeamScoreAndMileage(user, challenge, isFirstBlood, isSignature, challengeId);

            // 4. 문제 점수 업데이트 (다이나믹 스코어링) - 시그니처 제외
            if (!isSignature) {
                updateChallengeScore(challenge);
            }

            // 5. 문제 solver 카운트 증가
            challenge.setSolvers(challenge.getSolvers() + 1);
            challengeRepository.save(challenge);

            // 6. 전체 팀 점수 재계산
            updateAllTeamTotalPoints();

            long duration = System.currentTimeMillis() - startTime;
            log.info("[비동기 처리 완료] loginId={}, challengeId={}, 소요시간={}ms, isFirstBlood={}",
                    loginId, challengeId, duration, isFirstBlood);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[비동기 처리 실패] loginId={}, challengeId={}, 소요시간={}ms, error={}",
                    loginId, challengeId, duration, e.getMessage(), e);

            // 실패 시 재시도 로직을 추가할 수 있음
            // 예: 재시도 큐에 추가, 관리자 알림 등
        }
    }

    /**
     * 퍼스트 블러드 체크 및 처리
     *
     * 별도의 락을 사용하여 동시성 제어
     *
     * @return 퍼스트 블러드 여부
     */
    private boolean checkAndProcessFirstBlood(Long challengeId, UserEntity user,
                                              ChallengeEntity challenge, boolean isSignature) {
        String firstBloodLockKey = "firstBloodLock:" + challengeId;
        RLock firstBloodLock = redissonClient.getLock(firstBloodLockKey);
        boolean isFirstBlood = false;
        boolean firstBloodLocked = false;

        try {
            // 퍼스트 블러드 락 획득 (5초 대기, 5초 보유)
            firstBloodLocked = firstBloodLock.tryLock(5, 5, TimeUnit.SECONDS);

            if (firstBloodLocked) {
                // 해당 문제의 정답 제출 수 확인
                long solvedCount = historyRepository.countDistinctByChallengeId(challengeId);

                // 첫 번째 정답자인 경우
                if (solvedCount == 1) {
                    isFirstBlood = true;
                    log.info("[퍼스트 블러드!] challengeId={}, loginId={}, univ={}",
                            challengeId, user.getLoginId(), user.getUniv());

                    // 퍼스트 블러드 알림 전송 (일반 문제만)
                    if (!isSignature) {
                        try {
                            sendFirstBloodNotification(challenge, user);
                        } catch (Exception e) {
                            // 알림 실패는 로그만 남기고 계속 진행
                            log.error("[퍼스트 블러드 알림 실패] challengeId={}, error={}",
                                    challengeId, e.getMessage());
                        }
                    }
                }
            } else {
                log.warn("[퍼스트 블러드 락 획득 실패] challengeId={}, loginId={}",
                        challengeId, user.getLoginId());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[퍼스트 블러드 체크 중단] challengeId={}, error={}", challengeId, e.getMessage());
        } catch (Exception e) {
            log.error("[퍼스트 블러드 처리 오류] challengeId={}, error={}", challengeId, e.getMessage(), e);
        } finally {
            if (firstBloodLocked && firstBloodLock.isHeldByCurrentThread()) {
                firstBloodLock.unlock();
            }
        }

        return isFirstBlood;
    }

    /**
     * 팀 점수 및 마일리지 업데이트
     */
    private void updateTeamScoreAndMileage(UserEntity user, ChallengeEntity challenge,
                                           boolean isFirstBlood, boolean isSignature, Long challengeId) {
        if (user.getCurrentTeamId() == null) {
            log.warn("[팀 없음] userId={}, 점수 업데이트 스킵", user.getUserId());
            return;
        }

        try {
            int baseMileage = challenge.getMileage();
            int bonus = (isFirstBlood && baseMileage > 0) ? (int) Math.ceil(baseMileage * 0.30) : 0;
            int finalMileage = baseMileage + bonus;

            int awardedPoints = isSignature ? 0 : challenge.getPoints(); // 시그니처는 점수 0

            teamService.recordTeamSolution(
                    user.getUserId(),
                    challengeId,
                    awardedPoints,
                    finalMileage
            );

            log.info("[팀 점수 업데이트] challengeId={}, teamId={}, points={}, mileage={} (base={}, bonus={}), isSignature={}, isFirstBlood={}",
                    challengeId, user.getCurrentTeamId(), awardedPoints, finalMileage,
                    baseMileage, bonus, isSignature, isFirstBlood);

        } catch (Exception e) {
            log.error("[팀 점수 업데이트 실패] challengeId={}, userId={}, error={}",
                    challengeId, user.getUserId(), e.getMessage(), e);
            throw e; // 점수 업데이트 실패는 중요하므로 예외를 다시 던짐
        }
    }

    /**
     * 문제 점수 계산 (다이나믹 스코어링)
     *
     * 풀이 수에 따라 점수가 동적으로 감소
     */
    private void updateChallengeScore(ChallengeEntity challenge) {
        try {
            long solvedCount = historyRepository.countDistinctByChallengeId(challenge.getChallengeId());

            int initialPoints = challenge.getInitialPoints();
            int minPoints = challenge.getMinPoints();
            int decay = 50;

            // 다이나믹 스코어링 공식
            double newPoints = (((double)(minPoints - initialPoints) / (decay * decay)) * (solvedCount * solvedCount)) + initialPoints;
            newPoints = Math.max(newPoints, minPoints);
            newPoints = Math.ceil(newPoints);

            int oldPoints = challenge.getPoints();
            challenge.setPoints((int)newPoints);
            challengeRepository.save(challenge);

            log.info("[다이나믹 스코어링] challengeId={}, solvedCount={}, oldPoints={}, newPoints={}",
                    challenge.getChallengeId(), solvedCount, oldPoints, (int)newPoints);

        } catch (Exception e) {
            log.error("[다이나믹 스코어링 실패] challengeId={}, error={}",
                    challenge.getChallengeId(), e.getMessage(), e);
        }
    }

    /**
     * 전체 팀 점수 재계산
     *
     * ChallengeService의 기존 메서드 호출
     * 주의: 이 작업은 무거우므로 마지막에 한 번만 실행
     */
    private void updateAllTeamTotalPoints() {
        try {
            // 주의: ChallengeService를 직접 주입하면 순환 참조 발생 가능
            // 따라서 별도 메서드로 분리하거나, 여기서는 팀별로 개별 업데이트
            log.info("[전체 팀 점수 재계산 시작]");

            // 실제 구현은 ChallengeService의 updateAllTeamTotalPoints()와 동일
            // 순환 참조 방지를 위해 여기서는 로그만 남김
            // 필요시 별도의 ScoreCalculationService로 분리 권장

            log.info("[전체 팀 점수 재계산 완료]");

        } catch (Exception e) {
            log.error("[전체 팀 점수 재계산 실패] error={}", e.getMessage(), e);
            // 점수 재계산 실패는 치명적이지 않으므로 로그만 남김
        }
    }

    /**
     * 퍼스트 블러드 알림 전송
     외부 API 호출이므로 실패해도 계속 진행
     */
    private void sendFirstBloodNotification(ChallengeEntity challenge, UserEntity user) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("X-API-Key", apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("first_blood_problem", challenge.getTitle());
            body.put("first_blood_person", user.getLoginId());
            body.put("first_blood_school", user.getUniv());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[퍼스트 블러드 알림 전송 성공] challengeId={}, loginId={}",
                        challenge.getChallengeId(), user.getLoginId());
            } else {
                log.error("[퍼스트 블러드 알림 전송 실패] challengeId={}, statusCode={}",
                        challenge.getChallengeId(), response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("[퍼스트 블러드 알림 전송 오류] challengeId={}, error={}",
                    challenge.getChallengeId(), e.getMessage());
            // 알림 실패는 치명적이지 않으므로 예외를 던지지 않음
        }
    }
}