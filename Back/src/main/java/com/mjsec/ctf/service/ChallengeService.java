package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.ChallengeEntity;
import com.mjsec.ctf.domain.HistoryEntity;
import com.mjsec.ctf.domain.TeamHistoryEntity;
import com.mjsec.ctf.domain.SubmissionEntity;
import com.mjsec.ctf.domain.TeamEntity;
import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.dto.ChallengeDto;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.repository.*;
import com.mjsec.ctf.type.ErrorCode;
import io.micrometer.common.util.StringUtils;
import jakarta.transaction.Transactional;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class ChallengeService {

    private final TeamService teamService;
    private final FileService fileService;
    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final HistoryRepository historyRepository;
    private final TeamHistoryRepository teamHistoryRepository;
    private final SubmissionRepository submissionRepository;

    private final PasswordEncoder passwordEncoder;
    private final RedissonClient redissonClient;
    private final TeamRepository teamRepository;

    // ▼ 시그니처 코드/잠금
    private final TeamSignatureUnlockRepository unlockRepo;
    private final SignatureCodeRepository codeRepo;

    @Value("${api.key}")
    private String apiKey;

    @Value("${api.url}")
    private String apiUrl;

    // 현재 사용자 ID를 반환
    public String currentLoginId(){
        log.info("Getting user id from security context holder");
        String loginId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("Successfully returned login id from security context holder : {}", loginId);
        return loginId;
    }

    // 모든 문제 조회
    public Page<ChallengeDto.Simple> getAllChallengesOrderedById(Pageable pageable) {
        log.info("Getting all challenges ordered by Id ASC");

        Page<ChallengeEntity> challenges = challengeRepository.findAllByOrderByChallengeIdAsc(pageable);
        String currentLoginId = currentLoginId();

        return challenges.map(challenge -> {
            boolean solved = false;

            if (historyRepository.existsByLoginIdAndChallengeId(currentLoginId, challenge.getChallengeId())) {
                solved = true;
            } else {
                UserEntity user = userRepository.findByLoginId(currentLoginId)
                        .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

                if (user.getCurrentTeamId() == null) {
                    throw new RestApiException(ErrorCode.MUST_BE_BELONG_TEAM);
                } else {
                    Optional<TeamEntity> team = teamService.getUserTeam(user.getCurrentTeamId());   // 팀 단위로 확인
                    if (team.isPresent()) {
                        solved = team.get().hasSolvedChallenge(challenge.getChallengeId());
                    }
                }
            }

            return ChallengeDto.Simple.fromEntity(challenge, solved);
        });
    }

    // 시그니처 락 강제 체크
    private void assertSignatureUnlockedOrThrow(ChallengeEntity challenge) {
        if (challenge.getCategory() != com.mjsec.ctf.type.ChallengeCategory.SIGNATURE) return;

        String loginId = currentLoginId();
        UserEntity user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));
        if (user.getCurrentTeamId() == null) {
            throw new RestApiException(ErrorCode.MUST_BE_BELONG_TEAM);
        }

        boolean unlocked = unlockRepo.existsByTeamIdAndChallengeId(user.getCurrentTeamId(), challenge.getChallengeId());
        if (!unlocked) {
            // 시그니처 인증(언락) 필요
            throw new RestApiException(ErrorCode.FORBIDDEN);
        }
    }

    // 특정 문제 상세 조회
    public ChallengeDto.Detail getDetailChallenge(Long challengeId){
        log.info("Fetching details for challengeId: {}", challengeId);

        ChallengeEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));

        // SIGNATURE 접근 통제
        assertSignatureUnlockedOrThrow(challenge);

        return ChallengeDto.Detail.fromEntity(challenge);
    }

    // 문제 생성
    @Transactional
    public void createChallenge(MultipartFile file, ChallengeDto challengeDto) throws IOException {

        if (challengeDto == null) {
            throw new RestApiException(ErrorCode.REQUIRED_FIELD_NULL);
        }

        // 카테고리 확인
        com.mjsec.ctf.type.ChallengeCategory category;
        if (challengeDto.getCategory() != null && !challengeDto.getCategory().isBlank()) {
            try {
                category = com.mjsec.ctf.type.ChallengeCategory.valueOf(challengeDto.getCategory().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RestApiException(ErrorCode.BAD_REQUEST, "유효하지 않은 카테고리입니다.");
            }
        } else {
            category = com.mjsec.ctf.type.ChallengeCategory.MISC;
        }

        // 시그니처 문제는 club 필수
        boolean isSignature = category == com.mjsec.ctf.type.ChallengeCategory.SIGNATURE;
        if (isSignature && (challengeDto.getClub() == null || challengeDto.getClub().isBlank())) {
            throw new RestApiException(ErrorCode.BAD_REQUEST, "시그니처 문제는 club을 반드시 지정해야 합니다.");
        }

        // 시그니처도 mileage 값은 허용. 점수 필드는 0으로 두는 게 보통이지만(선택), 현재 로직상 제출 시 포인트는 무시됨.
        int points = isSignature ? 0 : challengeDto.getPoints();
        int minPoints = isSignature ? 0 : challengeDto.getMinPoints();
        int initialPoints = isSignature ? 0 : challengeDto.getInitialPoints();
        int mileage = challengeDto.getMileage();

        ChallengeEntity challenge = ChallengeEntity.builder()
                .title(challengeDto.getTitle())
                .description(challengeDto.getDescription())
                .flag(passwordEncoder.encode(challengeDto.getFlag()))
                .points(points)
                .minPoints(minPoints)
                .initialPoints(initialPoints)
                .startTime(challengeDto.getStartTime())
                .endTime(challengeDto.getEndTime())
                .url(challengeDto.getUrl())
                .category(category)
                .mileage(mileage)
                .club(challengeDto.getClub())
                .build(); // 저장

        if (file != null) {
            String fileUrl = fileService.store(file);
            challenge.setFileUrl(fileUrl);
        }

        challengeRepository.save(challenge);
    }

    // 문제 수정
    @Transactional
    public void updateChallenge(Long challengeId, MultipartFile file, ChallengeDto challengeDto) throws IOException {

        ChallengeEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));

        if (challengeDto != null) {
            // 카테고리 확인
            com.mjsec.ctf.type.ChallengeCategory category;
            if (challengeDto.getCategory() != null && !challengeDto.getCategory().isBlank()) {
                try {
                    category = com.mjsec.ctf.type.ChallengeCategory.valueOf(challengeDto.getCategory().toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new RestApiException(ErrorCode.BAD_REQUEST, "유효하지 않은 카테고리입니다.");
                }
            } else {
                category = challenge.getCategory();
            }

            boolean isSignature = category == com.mjsec.ctf.type.ChallengeCategory.SIGNATURE;

            // 새 club 값(없으면 기존 유지)
            String newClub = (challengeDto.getClub() != null) ? challengeDto.getClub() : challenge.getClub();
            if (isSignature && (newClub == null || newClub.isBlank())) {
                throw new RestApiException(ErrorCode.BAD_REQUEST, "시그니처 문제는 club을 반드시 지정해야 합니다.");
            }

            int points = isSignature ? 0 : challengeDto.getPoints();
            int minPoints = isSignature ? 0 : challengeDto.getMinPoints();
            int initialPoints = isSignature ? 0 : challengeDto.getInitialPoints();
            int mileage =  challengeDto.getMileage();

            ChallengeEntity updatedChallenge = ChallengeEntity.builder()
                    .challengeId(challenge.getChallengeId())
                    .title(challengeDto.getTitle())
                    .description(challengeDto.getDescription())
                    .flag(passwordEncoder.encode(challengeDto.getFlag()))
                    .points(points)
                    .minPoints(minPoints)
                    .initialPoints(initialPoints)
                    .startTime(challengeDto.getStartTime())
                    .endTime(challengeDto.getEndTime())
                    .url(challengeDto.getUrl())
                    .category(category)
                    .mileage(mileage)
                    .club(newClub)
                    .build();

            // 기존 파일 URL 유지
            updatedChallenge.setFileUrl(challenge.getFileUrl());
            challenge = updatedChallenge;

            // 일반 문제로 전환되면 시그니처 연관 데이터 정리
            if (!isSignature) {
                unlockRepo.deleteByChallengeId(challengeId);
                codeRepo.deleteByChallengeId(challengeId);
            }
        }

        if (file != null) {
            String fileUrl = fileService.store(file);
            challenge.setFileUrl(fileUrl);
        }

        challengeRepository.save(challenge);
    }

    // 문제 삭제
    @Transactional
    public void deleteChallenge(Long challengeId){
        log.info("문제 삭제 시작: challengeId = {}", challengeId);

        ChallengeEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));

        // 1) 시그니처 연관 데이터 먼저 정리 (자식 → 부모 순서)
        try {
            unlockRepo.deleteByChallengeId(challengeId);
        } catch (Exception e) {
            log.warn("unlock cleanup error (ignored): {}", e.toString());
        }
        try {
            codeRepo.deleteByChallengeId(challengeId);
        } catch (Exception e) {
            log.warn("code cleanup error (ignored): {}", e.toString());
        }

        // 2) 제출/히스토리 정리
        submissionRepository.deleteByChallengeId(challengeId);
        historyRepository.deleteByChallengeId(challengeId);

        // 3) 해당 문제를 푼 팀 정리 + 재계산
        List<TeamEntity> affectedTeams = teamRepository.findTeamsBySolvedChallengeId(
                String.valueOf(challengeId)
        );
        for (TeamEntity team : affectedTeams) {
            team.getSolvedChallengeIds().remove(challengeId);
            recalculateTeamPoints(team);
        }

        // 4) 마지막으로 챌린지 삭제
        challengeRepository.delete(challenge);

        log.info("문제 삭제 완료: challengeId = {}, 영향받은 팀: {}", challengeId, affectedTeams.size());
    }

    // 문제 파일 다운로드
    public byte[] downloadChallengeFile(Long challengeId) throws IOException {
        ChallengeEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));

        // SIGNATURE 접근 통제
        assertSignatureUnlockedOrThrow(challenge);

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

            // SIGNATURE 접근 통제
            assertSignatureUnlockedOrThrow(challenge);

            if (user.getRole() != null && user.getRole().equals("ROLE_ADMIN")) {
                // Admin은 플래그 검증만 하고 점수/기록은 남기지 않음
                if (passwordEncoder.matches(flag, challenge.getFlag())) {
                    log.info("Admin {} verified challenge {} - Correct", loginId, challengeId);
                    return "Correct";
                } else {
                    log.info("Admin {} verified challenge {} - Wrong", loginId, challengeId);
                    return "Wrong";
                }
            }

            if (user.getCurrentTeamId() == null) {
                throw new RestApiException(ErrorCode.MUST_BE_BELONG_TEAM);
            }
            // 팀 단위 중복 제출 방지
            Optional<TeamEntity> team = teamService.getUserTeam(user.getCurrentTeamId());
            if (team.isPresent() && team.get().hasSolvedChallenge(challengeId)) {
                return "Submitted";
            }

            // 기존 제출 기록 여부 확인 (새 객체 delete 예외 방지)
            Optional<SubmissionEntity> existingOpt =
                    submissionRepository.findByLoginIdAndChallengeId(loginId, challengeId);

            SubmissionEntity submission = existingOpt.orElseGet(() ->
                    SubmissionEntity.builder()
                            .loginId(loginId)
                            .challengeId(challengeId)
                            .attemptCount(0)
                            .lastAttemptTime(LocalDateTime.now())
                            .build()
            );

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


                if (team.isPresent()) {
                    TeamHistoryEntity teamHistory = TeamHistoryEntity.builder()
                            .teamName(team.get().getTeamName())
                            .challengeId(challenge.getChallengeId())
                            .solvedTime(LocalDateTime.now())
                            .build();

                    teamHistoryRepository.save(teamHistory);
                }


                //팀 점수로 업데이트
                if (user.getCurrentTeamId() != null) {
                    teamService.recordTeamSolution(user.getUserId(), challengeId, challenge.getPoints(),challenge.getMileage());
                }
                boolean isSignature = challenge.getCategory() == com.mjsec.ctf.type.ChallengeCategory.SIGNATURE;
                boolean isFirstBlood = false;

                //  퍼스트블러드 판정: 카테고리 무관하게 '보너스 계산'을 위해 판정
                String firstBloodLockKey = "firstBloodLock:" + challengeId;
                RLock firstBloodLock = redissonClient.getLock(firstBloodLockKey);
                boolean firstBloodLocked = false;

                try {
                    firstBloodLocked = firstBloodLock.tryLock(5, 5, TimeUnit.SECONDS);
                    if (firstBloodLocked) {
                        long solvedCount = historyRepository.countDistinctByChallengeId(challengeId);
                        if (solvedCount == 1) {
                            isFirstBlood = true; 
                            // 알림은 정책상 일반 문제만(원래대로 유지). 시그니처에도 보내고 싶으면 if 제거.
                            if (!isSignature) {
                                sendFirstBloodNotification(challenge, user);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    if (firstBloodLocked && firstBloodLock.isHeldByCurrentThread()) {
                        firstBloodLock.unlock();
                    }
                }

                // ── 팀 마일리지/점수 반영
                //    - 시그니처: 마일리지만 적립(점수 0으로 전달)
                //    - 일반 문제: 점수 + 마일리지 적립
                if (user.getCurrentTeamId() != null) {
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

                    log.info("Mileage award: challengeId={}, base={}, isFirstBlood={}, final={}, teamId={}, isSignature={}",
                            challengeId, baseMileage, isFirstBlood, finalMileage, user.getCurrentTeamId(), isSignature);
                }

                // 문제 점수 업데이트(다이나믹 스코어링): 시그니처는 제외 유지
                if (!isSignature) {
                    updateChallengeScore(challenge);
                }

                challenge.setSolvers(challenge.getSolvers() + 1);
                challengeRepository.save(challenge);

                // 기존 제출 기록만 삭제 (신규 객체는 저장도 안 했으니 삭제 불필요)
                existingOpt.ifPresent(submissionRepository::delete);

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

    // 문제 점수 계산기
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

    // 전체 팀 점수 재계산(여기서는 기존대로 시그니처 제외)
    @Transactional
    public void updateAllTeamTotalPoints() {
        log.info("전체 팀 점수 재계산 시작");

        List<TeamEntity> allTeams = teamService.getTeamRanking(); // 모든 팀 조회

        for (TeamEntity team : allTeams) {
            recalculateTeamPoints(team);
        }

        log.info("전체 팀 점수 재계산 완료");
    }

    @Transactional
    public void recalculateTeamPoints(TeamEntity team) {
        // 1. 팀이 푼 모든 문제를 한 번에 조회 (IN 쿼리)
        List<Long> solvedChallengeIds = team.getSolvedChallengeIds();
        if (solvedChallengeIds.isEmpty()) {
            team.setTotalPoint(0);
            team.setLastSolvedTime(null);
            teamService.saveTeam(team);
            return;
        }

        // [최적화] 한 번의 쿼리로 모든 문제 조회
        List<ChallengeEntity> challenges = challengeRepository.findAllById(solvedChallengeIds);
        Map<Long, ChallengeEntity> challengeMap = challenges.stream()
                .collect(Collectors.toMap(ChallengeEntity::getChallengeId, Function.identity()));

        // [최적화] 한 번의 쿼리로 관련 히스토리 모두 조회
        List<HistoryEntity> histories = historyRepository.findByChallengeIdIn(solvedChallengeIds);

        // [최적화] 팀원 ID로 한 번에 조회
        List<UserEntity> teamMembers = userRepository.findAllById(team.getMemberUserIds());
        Set<String> memberLoginIds = teamMembers.stream()
                .map(UserEntity::getLoginId)
                .collect(Collectors.toSet());

        // 2. 메모리에서 계산 — 시그니처는 점수 계산에서 제외(마일리지는 별도 필드라 여기서 건들 것 없음)
        int totalPoints = 0;
        LocalDateTime lastSolvedTime = null;

        for (Long cid : solvedChallengeIds) {
            ChallengeEntity c = challengeMap.get(cid);
            if (c == null) continue;

            if (c.getCategory() == com.mjsec.ctf.type.ChallengeCategory.SIGNATURE) {
                continue; // 점수 제외
            }

            totalPoints += c.getPoints();

            Optional<LocalDateTime> latestForThisChallenge = histories.stream()
                    .filter(h -> h.getChallengeId().equals(cid))
                    .filter(h -> memberLoginIds.contains(h.getLoginId()))
                    .map(HistoryEntity::getSolvedTime)
                    .max(Comparator.naturalOrder());

            if (latestForThisChallenge.isPresent()) {
                LocalDateTime solved = latestForThisChallenge.get();
                if (lastSolvedTime == null || solved.isAfter(lastSolvedTime)) {
                    lastSolvedTime = solved;
                }
            }
        }

        team.setTotalPoint(totalPoints);
        team.setLastSolvedTime(lastSolvedTime);
        teamService.saveTeam(team);
    }
}
