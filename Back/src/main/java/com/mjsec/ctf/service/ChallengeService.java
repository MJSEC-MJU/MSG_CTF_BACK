package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.ChallengeEntity;
import com.mjsec.ctf.domain.HistoryEntity;
import com.mjsec.ctf.domain.TeamHistoryEntity;
import com.mjsec.ctf.domain.SubmissionEntity;
import com.mjsec.ctf.domain.TeamEntity;
import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.dto.AdminSolveRecordDto;
import com.mjsec.ctf.dto.ChallengeDto;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.repository.*;
import com.mjsec.ctf.type.ErrorCode;
import com.mjsec.ctf.util.IPAddressUtil;
import io.micrometer.common.util.StringUtils;
import jakarta.persistence.EntityManager;
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
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class ChallengeService {

    private final EntityManager entityManager;
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

    // 시그니처 코드/잠금
    private final TeamSignatureUnlockRepository unlockRepo;
    private final SignatureCodeRepository codeRepo;

    // 공격 탐지 서비스
    private final ThreatDetectionService threatDetectionService;

    private final AsyncSubmissionProcessor asyncSubmissionProcessor;

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

    // 특정 문제 상세 조회 (일반 사용자용)
    public ChallengeDto.Detail getDetailChallenge(Long challengeId){
        log.info("Fetching details for challengeId: {}", challengeId);

        ChallengeEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));

        // SIGNATURE 접근 통제
        assertSignatureUnlockedOrThrow(challenge);

        // 실시간으로 solvers 카운트 계산 - 정확한 값 보장
        long actualSolvers = historyRepository.countDistinctByChallengeId(challengeId);
        challenge.setSolvers((int) actualSolvers);

        return ChallengeDto.Detail.fromEntity(challenge);
    }

    // 특정 문제 상세 조회 (관리자용 - 모든 필드 포함, flag 제외)
    public ChallengeDto.AdminDetail getAdminDetailChallenge(Long challengeId){
        log.info("Admin fetching full details for challengeId: {}", challengeId);

        ChallengeEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));

        return ChallengeDto.AdminDetail.fromEntity(challenge);
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
                .build();

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
                    .solvers(challenge.getSolvers())  // 기존 solvers 값 유지
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

        if (file != null && !file.isEmpty()) {
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
            teamService.recalculateTeamPoints(team);
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
    public String submit(String loginId, Long challengeId, String flag, String clientIP) {
        long startTime = System.currentTimeMillis();
        boolean isInternalIP = IPAddressUtil.isLocalIP(clientIP);

        //기본 검증 (락 없이 빠르게 처리)

        // 플래그 null/공백 체크
        if (flag == null || StringUtils.isBlank(flag)) {
            return "Flag cannot be null or empty";
        }

        // 사용자 조회
        UserEntity user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

        if (user.isEarlyExit()) {
            throw new RestApiException(ErrorCode.EARLY_EXIT_USER);
        }

        // 문제 조회
        ChallengeEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));

        // SIGNATURE 접근 통제
        assertSignatureUnlockedOrThrow(challenge);

        // 관리자는 플래그 검증만 하고 점수/기록은 남기지 않음
        if (user.getRole() != null && user.getRole().equals("ROLE_ADMIN")) {
            if (passwordEncoder.matches(flag, challenge.getFlag())) {
                log.info("Admin {} verified challenge {} - Correct", loginId, challengeId);
                return "Correct";
            } else {
                log.info("Admin {} verified challenge {} - Wrong", loginId, challengeId);
                return "Wrong";
            }
        }

        // 팀 소속 확인
        if (user.getCurrentTeamId() == null) {
            throw new RestApiException(ErrorCode.MUST_BE_BELONG_TEAM);
        }

        // 개인 중복 제출 방지 (락 없이 먼저 체크)
        if (historyRepository.existsByLoginIdAndChallengeId(loginId, challengeId)) {
            return "Submitted";
        }

        // 팀 단위 중복 제출 방지
        Optional<TeamEntity> team = teamService.getUserTeam(user.getCurrentTeamId());
        if (team.isPresent() && team.get().hasSolvedChallenge(challengeId)) {
            return "Submitted";
        }

        // 오답 처리 (락 없이 처리)

        // 플래그 검증
        if (!passwordEncoder.matches(flag, challenge.getFlag())) {
            // 오답 제출 시 공격 감지 시스템에 기록
            threatDetectionService.recordFlagAttempt(clientIP, false, challengeId, user.getUserId(), loginId, isInternalIP);

            // 기존 제출 기록 확인
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

            // Wait 체크 (브루트포스 방지)
            long secondsSinceLastAttempt = ChronoUnit.SECONDS.between(
                    submission.getLastAttemptTime(), LocalDateTime.now());

            if (submission.getAttemptCount() > 2 && secondsSinceLastAttempt < 30) {
                return "Wait";
            }

            // 오답 시도 횟수 증가
            submission.setAttemptCount(submission.getAttemptCount() + 1);
            submission.setLastAttemptTime(LocalDateTime.now());
            submissionRepository.save(submission);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[오답 처리] loginId={}, challengeId={}, 소요시간={}ms", loginId, challengeId, duration);

            return "Wrong";
        }

        //정답 처리 (최소한의 락만 사용)
        String lockKey = "challengeLock:" + challengeId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        boolean isFirstBlood = false;  // 락 안에서 판정
        int calculatedPoints = 0;       // 락 안에서 계산된 최신 점수

        try {
            long lockStartTime = System.currentTimeMillis();
            // 락 획득 (5초 대기, 10초 보유)
            // 기존: tryLock(10, 10) → 변경: tryLock(5, 10)
            // 대기 시간을 줄여서 빠르게 실패하도록 함
            locked = lock.tryLock(5, 10, TimeUnit.SECONDS);
            long lockWaitTime = System.currentTimeMillis() - lockStartTime;

            if (!locked) {
                log.warn("[락 획득 실패] loginId={}, challengeId={}, waitTime={}ms", loginId, challengeId, lockWaitTime);
                return "Try again later";
            }

            log.info("[락 획득 성공] loginId={}, challengeId={}, waitTime={}ms, lockKey={}",
                    loginId, challengeId, lockWaitTime, lockKey);

            // 락 획득 후 다시 한 번 중복 체크 (동시 요청 방지)
            if (historyRepository.existsByLoginIdAndChallengeId(loginId, challengeId)) {
                return "Submitted";
            }

            // 정답 제출 기록 (공격 감지 방지)
            threatDetectionService.recordFlagAttempt(clientIP, true, challengeId, user.getUserId(), loginId, isInternalIP);

            // HistoryEntity 저장 (가장 중요한 작업만 락 안에서 수행)
            HistoryEntity history = HistoryEntity.builder()
                    .loginId(user.getLoginId())
                    .challengeId(challenge.getChallengeId())
                    .solvedTime(LocalDateTime.now())
                    .univ(user.getUniv())
                    .build();
            historyRepository.save(history);

            // TeamHistory 저장
            if (team.isPresent()) {
                TeamHistoryEntity teamHistory = TeamHistoryEntity.builder()
                        .teamName(team.get().getTeamName())
                        .challengeId(challenge.getChallengeId())
                        .solvedTime(LocalDateTime.now())
                        .build();
                teamHistoryRepository.save(teamHistory);
            }

            // 기존 제출 기록 삭제 (오답 시도 기록)
            Optional<SubmissionEntity> existingOpt =
                    submissionRepository.findByLoginIdAndChallengeId(loginId, challengeId);
            existingOpt.ifPresent(submissionRepository::delete);

            // 락 안에서 Challenge를 비관적 락으로 다시 조회
            // 락 밖에서 조회한 challenge 객체는 stale data이므로 다시 조회 필수!
            ChallengeEntity lockedChallenge = challengeRepository.findByIdWithLock(challengeId)
                    .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));

            boolean isSignature = (lockedChallenge.getCategory() == com.mjsec.ctf.type.ChallengeCategory.SIGNATURE);

            // 🔴 퍼스트 블러드 판정 (solvers 증가 이전에 체크)
            isFirstBlood = (lockedChallenge.getSolvers() == 0);

            // solvers 증가 (DB에서 최신 값 기준)
            lockedChallenge.setSolvers(lockedChallenge.getSolvers() + 1);

            // 다이나믹 스코어링 (증가된 solvers 기준으로 직접 계산)
            if (!isSignature) {
                int initialPoints = lockedChallenge.getInitialPoints();
                int minPoints = lockedChallenge.getMinPoints();
                int decay = 50;
                long solvedCount = lockedChallenge.getSolvers();  // 🔴 증가된 solvers 값 사용

                double newPoints = (((double)(minPoints - initialPoints) / (decay * decay)) * (solvedCount * solvedCount)) + initialPoints;
                newPoints = Math.max(newPoints, minPoints);
                newPoints = Math.ceil(newPoints);

                lockedChallenge.setPoints((int)newPoints);
            }

            challengeRepository.save(lockedChallenge);

            // 🔴 Challenge 변경사항을 DB에 즉시 반영 (다음 요청이 최신 solvers를 읽을 수 있도록)
            entityManager.flush();
            log.info("[락 내부 - Challenge flush 완료] challengeId={}, newSolvers={}, newPoints={}",
                    challengeId, lockedChallenge.getSolvers(), lockedChallenge.getPoints());

            // 🔴 계산된 최신 점수 저장 (비동기로 전달하기 위함)
            calculatedPoints = lockedChallenge.getPoints();

            // 🔴 마일리지 계산 (퍼스트 블러드 보너스 포함)
            int baseMileage = Math.max(0, lockedChallenge.getMileage());
            int fbBonus = (isFirstBlood && baseMileage > 0) ? (int) Math.ceil(baseMileage * 0.30) : 0;
            int finalMileage = baseMileage + fbBonus;
            int awardedPoints = isSignature ? 0 : calculatedPoints;

            // 🔴 지금 막 푼 팀의 솔루션 기록 (락 안에서!)
            teamService.recordTeamSolution(user.getUserId(), challengeId, awardedPoints, finalMileage);

            // 🔴 JPA 변경사항을 DB에 즉시 반영 (flush)
            entityManager.flush();
            log.info("[락 내부 - flush 완료] challengeId={}, userId={}", challengeId, user.getUserId());

            // 🔴 문제 점수가 변경되었으므로 이 문제를 푼 모든 팀의 점수 재계산 (락 안에서!)
            if (!isSignature) {
                teamService.recalculateTeamsByChallenge(challengeId);
                log.info("[락 내부 - 팀 점수 재계산 완료] challengeId={}, newPoints={}",
                        challengeId, calculatedPoints);
            }

            log.info("[락 내부 - solvers 업데이트] challengeId={}, newSolvers={}, newPoints={}, isFirstBlood={}",
                    challengeId, lockedChallenge.getSolvers(), lockedChallenge.getPoints(), isFirstBlood);

            long lockDuration = System.currentTimeMillis() - startTime;
            log.info("[락 내부 처리 완료] loginId={}, challengeId={}, 소요시간={}ms",
                    loginId, challengeId, lockDuration);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[제출 처리 중단] loginId={}, challengeId={}, error={}",
                    loginId, challengeId, e.getMessage());
            return "Error while processing";
        } finally {
            // 락 해제
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        // 무거운 작업은 비동기로 처리 (락 밖에서 실행)
        try {
            // AsyncSubmissionProcessor를 통해 비동기 처리
            // 🔴 락 안에서 판정된 퍼스트 블러드와 계산된 점수를 전달
            asyncSubmissionProcessor.processCorrectSubmissionAsync(
                    user.getUserId(),
                    challengeId,
                    loginId,
                    isFirstBlood,
                    calculatedPoints
            );
        } catch (Exception e) {
            // 비동기 작업 스케줄링 실패 시 로그만 남기고 계속 진행
            // 사용자에게는 정답 처리된 것으로 표시됨
            log.error("[비동기 작업 스케줄링 실패] loginId={}, challengeId={}, error={}",
                    loginId, challengeId, e.getMessage(), e);
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        log.info("[정답 처리 완료] loginId={}, challengeId={}, 전체소요시간={}ms (비동기 작업 제외)",
                loginId, challengeId, totalDuration);

        // 즉시 정답 응답 반환 (점수 계산 등은 백그라운드에서 처리 중)
        return "Correct";
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
        teamService.recalculateAllTeamPoints();
        log.info("전체 팀 점수 재계산 완료");
    }

    // 관리자: 전체 제출 기록 조회
    public List<AdminSolveRecordDto> getAllSolveRecords() {
        log.info("관리자: 전체 제출 기록 조회 시작");

        List<HistoryEntity> allHistories = historyRepository.findAllByOrderBySolvedTimeAsc();
        Map<Long, ChallengeEntity> challengeMap = new HashMap<>();
        Map<Long, Long> firstBloodMap = new HashMap<>(); // challengeId -> firstBloodHistoryId

        // 모든 문제별 퍼스트 블러드 계산
        allHistories.stream()
                .filter(h -> h.getLoginId() != null)
                .collect(Collectors.groupingBy(HistoryEntity::getChallengeId))
                .forEach((challengeId, histories) -> {
                    Optional<HistoryEntity> firstBlood = histories.stream()
                            .min(Comparator.comparing(HistoryEntity::getSolvedTime));
                    firstBlood.ifPresent(h -> firstBloodMap.put(challengeId, h.getId()));
                });

        List<AdminSolveRecordDto> records = new ArrayList<>();

        for (HistoryEntity history : allHistories) {
            if (history.getLoginId() == null) continue;

            Optional<UserEntity> userOpt = userRepository.findByLoginId(history.getLoginId());
            if (!userOpt.isPresent()) continue;

            // 문제 정보 캐싱
            ChallengeEntity challenge = challengeMap.computeIfAbsent(
                    history.getChallengeId(),
                    id -> challengeRepository.findById(id).orElse(null)
            );
            if (challenge == null) continue;

            UserEntity user = userOpt.get();
            Long teamId = user.getCurrentTeamId();
            String teamName = null;

            if (teamId != null) {
                Optional<TeamEntity> teamOpt = teamRepository.findById(teamId);
                teamName = teamOpt.map(TeamEntity::getTeamName).orElse(null);
            }

            // 퍼스트 블러드 여부 확인
            Long firstBloodHistoryId = firstBloodMap.get(history.getChallengeId());
            boolean isFirstBlood = firstBloodHistoryId != null && firstBloodHistoryId.equals(history.getId());

            // 점수와 마일리지 계산
            int pointsAwarded = challenge.getCategory() == com.mjsec.ctf.type.ChallengeCategory.SIGNATURE ? 0 : challenge.getPoints();
            int baseMileage = challenge.getMileage();
            int mileageBonus = 0;

            if (isFirstBlood && baseMileage > 0) {
                mileageBonus = (int) Math.ceil(baseMileage * 0.30);
            }

            AdminSolveRecordDto record = AdminSolveRecordDto.builder()
                    .historyId(history.getId())
                    .challengeId(challenge.getChallengeId())
                    .challengeTitle(challenge.getTitle())
                    .loginId(history.getLoginId())
                    .teamName(teamName)
                    .teamId(teamId)
                    .univ(history.getUniv())
                    .solvedTime(history.getSolvedTime())
                    .pointsAwarded(pointsAwarded)
                    .mileageAwarded(baseMileage)
                    .mileageBonus(mileageBonus)
                    .isFirstBlood(isFirstBlood)
                    .build();

            records.add(record);
        }

        log.info("관리자: 전체 제출 기록 {} 건 조회 완료", records.size());
        return records;
    }

    // 관리자: 특정 문제의 모든 제출 기록 조회
    public List<AdminSolveRecordDto> getSolveRecordsByChallenge(Long challengeId) {
        log.info("관리자: 문제 {}의 제출 기록 조회 시작", challengeId);

        ChallengeEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));

        List<HistoryEntity> histories = historyRepository.findByChallengeId(challengeId);
        List<AdminSolveRecordDto> records = new ArrayList<>();

        // 퍼스트 블러드 찾기 (가장 빠른 제출)
        Optional<HistoryEntity> firstBloodOpt = histories.stream()
                .filter(h -> h.getLoginId() != null)
                .min(Comparator.comparing(HistoryEntity::getSolvedTime));

        Long firstBloodHistoryId = firstBloodOpt.map(HistoryEntity::getId).orElse(null);

        for (HistoryEntity history : histories) {
            if (history.getLoginId() == null) continue; // 삭제된 유저는 스킵

            Optional<UserEntity> userOpt = userRepository.findByLoginId(history.getLoginId());
            if (!userOpt.isPresent()) continue;

            UserEntity user = userOpt.get();
            Long teamId = user.getCurrentTeamId();
            String teamName = null;

            if (teamId != null) {
                Optional<TeamEntity> teamOpt = teamRepository.findById(teamId);
                teamName = teamOpt.map(TeamEntity::getTeamName).orElse(null);
            }

            // 퍼스트 블러드 여부 확인
            boolean isFirstBlood = firstBloodHistoryId != null && firstBloodHistoryId.equals(history.getId());

            // 당시 획득한 점수와 마일리지 계산
            int pointsAwarded = challenge.getCategory() == com.mjsec.ctf.type.ChallengeCategory.SIGNATURE ? 0 : challenge.getPoints();
            int baseMileage = challenge.getMileage();
            int mileageBonus = 0;

            if (isFirstBlood && baseMileage > 0) {
                mileageBonus = (int) Math.ceil(baseMileage * 0.30);
            }

            AdminSolveRecordDto record = AdminSolveRecordDto.builder()
                    .historyId(history.getId())
                    .challengeId(challenge.getChallengeId())
                    .challengeTitle(challenge.getTitle())
                    .loginId(history.getLoginId())
                    .teamName(teamName)
                    .teamId(teamId)
                    .univ(history.getUniv())
                    .solvedTime(history.getSolvedTime())
                    .pointsAwarded(pointsAwarded)
                    .mileageAwarded(baseMileage)
                    .mileageBonus(mileageBonus)
                    .isFirstBlood(isFirstBlood)
                    .build();

            records.add(record);
        }

        log.info("관리자: 문제 {}의 제출 기록 {} 건 조회 완료", challengeId, records.size());
        return records;
    }

    // 관리자: 특정 사용자의 특정 문제 제출 기록 철회
    @Transactional
    public void revokeSolveRecord(Long challengeId, String loginId) {
        log.info("관리자: 문제 {} 사용자 {} 제출 기록 철회 시작", challengeId, loginId);

        ChallengeEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));

        UserEntity user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

        // 1. HistoryEntity 조회 및 퍼스트 블러드 여부 확인
        Optional<HistoryEntity> historyOpt = historyRepository.findByLoginIdAndChallengeId(loginId, challengeId);
        if (!historyOpt.isPresent()) {
            throw new RestApiException(ErrorCode.BAD_REQUEST, "해당 사용자의 문제 풀이 기록이 없습니다.");
        }

        HistoryEntity history = historyOpt.get();

        // 퍼스트 블러드 여부 확인: 해당 문제의 모든 제출 기록 중 가장 빠른지 체크
        List<HistoryEntity> allHistories = historyRepository.findByChallengeId(challengeId);
        boolean wasFirstBlood = allHistories.stream()
                .filter(h -> h.getLoginId() != null)
                .min(Comparator.comparing(HistoryEntity::getSolvedTime))
                .map(h -> h.getId().equals(history.getId()))
                .orElse(false);

        log.info("삭제 대상 퍼스트 블러드 여부: {}", wasFirstBlood);

        historyRepository.delete(history);
        log.info("HistoryEntity 삭제 완료: historyId={}", history.getId());

        // 2. TeamHistoryEntity 조회 및 삭제
        if (user.getCurrentTeamId() != null) {
            Optional<TeamEntity> teamOpt = teamRepository.findById(user.getCurrentTeamId());
            if (teamOpt.isPresent()) {
                TeamEntity team = teamOpt.get();
                String teamName = team.getTeamName();

                List<TeamHistoryEntity> teamHistories = teamHistoryRepository.findByTeamNameAndChallengeId(teamName, challengeId);
                if (!teamHistories.isEmpty()) {
                    // 가장 최근 것을 삭제 (또는 모두 삭제할 수도 있음)
                    TeamHistoryEntity teamHistory = teamHistories.get(0);
                    teamHistoryRepository.delete(teamHistory);
                    log.info("TeamHistoryEntity 삭제 완료: teamHistoryId={}", teamHistory.getHistoryid());
                }

                // 3. 팀에서 해당 문제 제거 및 점수/마일리지 복구
                int pointsToDeduct = challenge.getCategory() == com.mjsec.ctf.type.ChallengeCategory.SIGNATURE ? 0 : challenge.getPoints();
                int baseMileage = challenge.getMileage();

                // 퍼스트 블러드 보너스 계산 (30% 추가)
                int mileageBonus = 0;
                if (wasFirstBlood && baseMileage > 0) {
                    mileageBonus = (int) Math.ceil(baseMileage * 0.30);
                }
                int mileageToDeduct = baseMileage + mileageBonus;

                team.revokeSolvedChallenge(challengeId, pointsToDeduct, mileageToDeduct);
                teamRepository.save(team);
                log.info("팀 점수/마일리지 복구 완료: teamId={}, points={}, baseMileage={}, bonus={}, totalMileage={}",
                        team.getTeamId(), pointsToDeduct, baseMileage, mileageBonus, mileageToDeduct);
            }
        }

        // 4. Challenge의 solvers 카운트 감소
        challenge.setSolvers(Math.max(0, challenge.getSolvers() - 1));
        challengeRepository.save(challenge);
        log.info("Challenge solvers 감소 완료: challengeId={}, solvers={}", challengeId, challenge.getSolvers());

        // 5. 다이나믹 스코어링 재계산 (SIGNATURE 제외)
        if (challenge.getCategory() != com.mjsec.ctf.type.ChallengeCategory.SIGNATURE) {
            updateChallengeScore(challenge);
            log.info("다이나믹 스코어 재계산 완료: challengeId={}, newPoints={}", challengeId, challenge.getPoints());
        }

        // 6. 영향받은 모든 팀의 점수 재계산
        teamService.recalculateAllTeamPoints();

        //새로운 퍼스트 블러드에게 보너스 지급
        if (wasFirstBlood) {
            // 삭제 후 남은 제출 기록 중 가장 빠른 것 찾기
            List<HistoryEntity> remainingHistories = historyRepository.findByChallengeId(challengeId);

            Optional<HistoryEntity> newFirstBloodOpt = remainingHistories.stream()
                    .filter(h -> h.getLoginId() != null)
                    .min(Comparator.comparing(HistoryEntity::getSolvedTime));

            if (newFirstBloodOpt.isPresent()) {
                HistoryEntity newFirstBloodHistory = newFirstBloodOpt.get();

                Optional<UserEntity> newFirstUserOpt = userRepository.findByLoginId(newFirstBloodHistory.getLoginId());

                if (newFirstUserOpt.isPresent()) {
                    UserEntity newFirstUser = newFirstUserOpt.get();

                    if (newFirstUser.getCurrentTeamId() != null) {
                        Optional<TeamEntity> newFirstTeamOpt = teamRepository.findById(newFirstUser.getCurrentTeamId());

                        if (newFirstTeamOpt.isPresent()) {
                            TeamEntity newFirstTeam = newFirstTeamOpt.get();

                            // 보너스 마일리지 계산 (30%)
                            int baseMileage = challenge.getMileage();
                            int bonus = (int) Math.ceil(baseMileage * 0.30);

                            // 새 퍼스트 블러드 팀에게 보너스만 추가 지급
                            // (기본 마일리지는 이미 받았으므로 보너스만 추가)
                            newFirstTeam.addMileage(bonus);
                            teamRepository.save(newFirstTeam);

                            log.info("새 퍼스트 블러드 보너스 지급: teamId={}, teamName={}, bonus={}, loginId={}",
                                    newFirstTeam.getTeamId(), newFirstTeam.getTeamName(), bonus, newFirstBloodHistory.getLoginId());
                        }
                    }
                }
            } else {
                log.info("삭제 후 남은 제출 기록이 없음: challengeId={}", challengeId);
            }
        }

        log.info("관리자: 문제 {} 사용자 {} 제출 기록 철회 완료", challengeId, loginId);
    }

    // 관리자: 특정 사용자의 모든 제출 기록 삭제
    @Transactional
    public int revokeAllSolveRecordsByUser(String loginId) {
        log.info("관리자: 사용자 {}의 모든 제출 기록 삭제 시작", loginId);

        UserEntity user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

        // 1. 사용자의 모든 히스토리 조회
        List<HistoryEntity> userHistories = historyRepository.findByLoginId(loginId);

        if (userHistories.isEmpty()) {
            log.info("사용자 {}의 제출 기록이 없습니다.", loginId);
            return 0;
        }

        int deletedCount = 0;
        Set<Long> affectedChallengeIds = new HashSet<>();

        // 2. 각 히스토리마다 철회 로직 실행
        for (HistoryEntity history : userHistories) {
            Long challengeId = history.getChallengeId();
            affectedChallengeIds.add(challengeId);

            Optional<ChallengeEntity> challengeOpt = challengeRepository.findById(challengeId);
            if (!challengeOpt.isPresent()) {
                log.warn("문제 {}를 찾을 수 없습니다. 히스토리만 삭제합니다.", challengeId);
                historyRepository.delete(history);
                deletedCount++;
                continue;
            }

            ChallengeEntity challenge = challengeOpt.get();

            // 퍼스트 블러드 여부 확인
            List<HistoryEntity> allHistories = historyRepository.findByChallengeId(challengeId);
            boolean isFirstBlood = allHistories.stream()
                    .filter(h -> h.getLoginId() != null)
                    .min(Comparator.comparing(HistoryEntity::getSolvedTime))
                    .map(h -> h.getId().equals(history.getId()))
                    .orElse(false);

            // 히스토리 삭제
            historyRepository.delete(history);
            log.info("HistoryEntity 삭제: historyId={}, challengeId={}", history.getId(), challengeId);

            // TeamHistoryEntity 삭제
            if (user.getCurrentTeamId() != null) {
                Optional<TeamEntity> teamOpt = teamRepository.findById(user.getCurrentTeamId());
                if (teamOpt.isPresent()) {
                    TeamEntity team = teamOpt.get();
                    String teamName = team.getTeamName();

                    List<TeamHistoryEntity> teamHistories = teamHistoryRepository.findByTeamNameAndChallengeId(teamName, challengeId);
                    if (!teamHistories.isEmpty()) {
                        TeamHistoryEntity teamHistory = teamHistories.get(0);
                        teamHistoryRepository.delete(teamHistory);
                        log.info("TeamHistoryEntity 삭제: teamHistoryId={}", teamHistory.getHistoryid());
                    }

                    // 팀에서 해당 문제 제거 및 점수/마일리지 복구
                    int pointsToDeduct = challenge.getCategory() == com.mjsec.ctf.type.ChallengeCategory.SIGNATURE ? 0 : challenge.getPoints();
                    int baseMileage = challenge.getMileage();
                    int mileageBonus = 0;
                    if (isFirstBlood && baseMileage > 0) {
                        mileageBonus = (int) Math.ceil(baseMileage * 0.30);
                    }
                    int mileageToDeduct = baseMileage + mileageBonus;

                    team.revokeSolvedChallenge(challengeId, pointsToDeduct, mileageToDeduct);
                    teamRepository.save(team);
                    log.info("팀 점수/마일리지 복구: teamId={}, challengeId={}, points={}, mileage={}",
                            team.getTeamId(), challengeId, pointsToDeduct, mileageToDeduct);
                }
            }

            // Challenge solvers 감소
            challenge.setSolvers(Math.max(0, challenge.getSolvers() - 1));
            challengeRepository.save(challenge);

            deletedCount++;
        }

        // 3. 영향받은 모든 문제의 다이나믹 스코어 재계산
        for (Long challengeId : affectedChallengeIds) {
            Optional<ChallengeEntity> challengeOpt = challengeRepository.findById(challengeId);
            if (challengeOpt.isPresent()) {
                ChallengeEntity challenge = challengeOpt.get();
                if (challenge.getCategory() != com.mjsec.ctf.type.ChallengeCategory.SIGNATURE) {
                    updateChallengeScore(challenge);
                }
            }
        }

        // 4. 전체 팀 점수 재계산
        updateAllTeamTotalPoints();

        log.info("관리자: 사용자 {}의 제출 기록 {} 건 삭제 완료", loginId, deletedCount);
        return deletedCount;
    }
}
