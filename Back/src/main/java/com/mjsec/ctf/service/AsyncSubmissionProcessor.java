package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.ChallengeEntity;
import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.repository.ChallengeRepository;
import com.mjsec.ctf.repository.HistoryRepository;
import com.mjsec.ctf.repository.UserRepository;
import com.mjsec.ctf.type.ChallengeCategory;
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
 * 비동기 제출 처리 전담 서비스 (경합 해결 버전)
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

    @Async("submissionAsyncExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processCorrectSubmissionAsync(Long userId, Long challengeId, String loginId, boolean isFirstBlood, int calculatedPoints) {
        final long startedAt = System.currentTimeMillis();
        final String lockKey = "challenge:submit:lock:" + challengeId;
        final RLock lock = redissonClient.getFairLock(lockKey);

        boolean locked = false;
        try {
            locked = lock.tryLock(10, 15, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("[LOCK 획득 실패] challengeId={}, loginId={}", challengeId, loginId);
                return;
            }

            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("User not found: " + userId));

            ChallengeEntity challenge = challengeRepository.findById(challengeId)
                    .orElseThrow(() -> new IllegalStateException("Challenge not found: " + challengeId));

            final boolean isSignature = (challenge.getCategory() == ChallengeCategory.SIGNATURE);

            // 🔴 퍼스트 블러드 판정과 점수 계산은 락 안에서 이미 완료됨 (파라미터로 전달받음)
            // 비동기에서는 전달받은 값들을 그대로 사용

            // 퍼스트 블러드 알림 전송
            if (isFirstBlood && !isSignature) {
                try {
                    sendFirstBloodNotification(challenge, user);
                    log.info("[퍼블 알림 전송] challengeId={}, by={}", challengeId, user.getLoginId());
                } catch (Exception nf) {
                    log.warn("[퍼블 알림 실패] challengeId={}, err={}", challengeId, nf.getMessage());
                }
            }

            // 팀 점수 & 마일리지 업데이트 (락 안에서 계산된 점수 사용)
            applyTeamScoreAndMileage(user, challenge, isFirstBlood, isSignature, calculatedPoints);

            log.info("[비동기 처리 완료] loginId={}, challengeId={}, duration={}ms, isFB={}, calculatedPoints={}",
                    loginId, challengeId, (System.currentTimeMillis() - startedAt),
                    isFirstBlood, calculatedPoints);

        } catch (Exception e) {
            log.error("[비동기 처리 실패] challengeId={}, loginId={}, dur={}ms, err={}",
                    challengeId, loginId, (System.currentTimeMillis() - startedAt), e.getMessage(), e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                try { lock.unlock(); } catch (Exception ignore) {}
            }
        }
    }

    private void applyTeamScoreAndMileage(UserEntity user, ChallengeEntity challenge,
                                          boolean isFirstBlood, boolean isSignature, int newPoints) {
        if (user.getCurrentTeamId() == null) {
            log.warn("[팀 없음] userId={}, 점수/마일리지 스킵", user.getUserId());
            return;
        }

        // ⬇️ 수정 포인트: getMileage()가 primitive int 이므로 null 비교 제거
        int baseMileage = Math.max(0, challenge.getMileage());
        int fbBonus     = (isFirstBlood && baseMileage > 0) ? (int) Math.ceil(baseMileage * 0.30) : 0;
        int finalMileage = baseMileage + fbBonus;

        int awardedPoints = isSignature ? 0 : newPoints;

        teamService.recordTeamSolution(
                user.getUserId(),
                challenge.getChallengeId(),
                awardedPoints,
                finalMileage
        );

        teamService.recalculateTeamsByChallenge(challenge.getChallengeId());

        log.info("[팀 반영] teamId={}, chall={}, points={}, mileage={} (base={}, fbBonus={}, isSig={}, isFB={})",
                user.getCurrentTeamId(), challenge.getChallengeId(),
                awardedPoints, finalMileage, baseMileage, fbBonus, isSignature, isFirstBlood);
    }

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
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[퍼블 알림 성공] challengeId={}, loginId={}", challenge.getChallengeId(), user.getLoginId());
            } else {
                log.error("[퍼블 알림 실패] challengeId={}, statusCode={}", challenge.getChallengeId(), response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("[퍼블 알림 오류] challengeId={}, err={}", challenge.getChallengeId(), e.getMessage());
        }
    }
}
