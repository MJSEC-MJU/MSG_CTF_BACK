package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.ChallengeEntity;
import com.mjsec.ctf.domain.HistoryEntity;
import com.mjsec.ctf.domain.SubmissionEntity;
import com.mjsec.ctf.domain.TeamEntity;
import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.dto.ChallengeDto;
//import com.mjsec.ctf.domain.LeaderboardEntity;    //개인용 주석처리
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.repository.*;
//import com.mjsec.ctf.repository.LeaderboardRepository;    //개인용 주석처리
import com.mjsec.ctf.type.ErrorCode;
import io.micrometer.common.util.StringUtils;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    private final TeamService teamService;
    private final FileService fileService;
    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final HistoryRepository historyRepository;
    //private final LeaderboardRepository leaderboardRepository;
    private final SubmissionRepository submissionRepository;

    /*
    8월 19일자 테스트할 땐
    BcryptPasswordEncoder -> PasswordEncoder로 변경해서 진행했음.
    (혹시 몰라 메모해둠)
     */
    private final PasswordEncoder passwordEncoder;
    private final RedissonClient redissonClient;
    private final TeamRepository teamRepository;

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
            }
            else {
                UserEntity user = userRepository.findByLoginId(currentLoginId)
                        .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

                if (user.getCurrentTeamId() == null) {
                    throw new RestApiException(ErrorCode.MUST_BE_BELONG_TEAM);
                }
                else {
                    Optional<TeamEntity> team = teamService.getUserTeam(user.getCurrentTeamId());   //마찬가지로 user개인이 아닌 team단위로 확인해야됨.
                    if (team.isPresent()) {
                        solved = team.get().hasSolvedChallenge(challenge.getChallengeId());
                    }
                }
            }

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
    @Transactional
    public void createChallenge(MultipartFile file, ChallengeDto challengeDto) throws IOException {

        if(challengeDto == null) {
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

        // 시그니처 문제는 점수 필드를 0으로 설정
        boolean isSignature = category == com.mjsec.ctf.type.ChallengeCategory.SIGNATURE;
        int points = isSignature ? 0 : challengeDto.getPoints();
        int minPoints = isSignature ? 0 : challengeDto.getMinPoints();
        int initialPoints = isSignature ? 0 : challengeDto.getInitialPoints();
        int mileage = challengeDto.getMileage();

        ChallengeEntity.ChallengeEntityBuilder builder = ChallengeEntity.builder()  //유지보수어려워 다시 원복
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
                .mileage(mileage);

        ChallengeEntity challenge = builder.build();

        if(file != null) {
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

        if(challengeDto != null) {
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

            // 시그니처 문제는 점수 필드를 0으로 설정
            boolean isSignature = category == com.mjsec.ctf.type.ChallengeCategory.SIGNATURE;
            int points = isSignature ? 0 : challengeDto.getPoints();
            int minPoints = isSignature ? 0 : challengeDto.getMinPoints();
            int initialPoints = isSignature ? 0 : challengeDto.getInitialPoints();
            int mileage =  challengeDto.getMileage();

            // 새 빌더를 이용해 수정된 엔티티 생성 (ID는 유지)
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
                    .build();

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
    @Transactional
    public void deleteChallenge(Long challengeId){
        log.info("문제 삭제 시작: challengeId = {}", challengeId);

        ChallengeEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));

        // Challenge 먼저 soft delete
        challengeRepository.delete(challenge);
        challengeRepository.flush();

        //관련 데이터 삭제
        submissionRepository.deleteByChallengeId(challengeId);
        historyRepository.deleteByChallengeId(challengeId);

        // 해당 문제를 푼 팀들만 찾아서 재계산
        List<TeamEntity> affectedTeams = teamRepository.findTeamsBySolvedChallengeId(
                String.valueOf(challengeId)
        );

        for (TeamEntity team : affectedTeams) {
            // solvedChallengeIds에서 제거
            team.getSolvedChallengeIds().remove(challengeId);

            // 전체 재계산
            recalculateTeamPoints(team);
        }

        log.info("문제 삭제 완료: challengeId = {}, 영향받은 팀: {}",
                challengeId, affectedTeams.size());
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
            //팀이 문제를 풀었는지 확인해야되는데 개인이 풀었는지 확인하고 있었음.
            Optional<TeamEntity> team = teamService.getUserTeam(user.getCurrentTeamId());
            if (team.isPresent() && team.get().hasSolvedChallenge(challengeId)) {
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
            }
            else {
                HistoryEntity history = HistoryEntity.builder()
                        .loginId(user.getLoginId())
                        .challengeId(challenge.getChallengeId())
                        .solvedTime(LocalDateTime.now())
                        .univ(user.getUniv())
                        .build();
                historyRepository.save(history);

                // 시그니처 문제는 점수 계산에서 제외
                boolean isSignature = challenge.getCategory() == com.mjsec.ctf.type.ChallengeCategory.SIGNATURE;

                //팀 점수로 업데이트 (시그니처 제외)
                if (user.getCurrentTeamId() != null && !isSignature) {
                    teamService.recordTeamSolution(user.getUserId(), challengeId, challenge.getPoints(), challenge.getMileage());
                }

                // firstBloodLock 안전하게 처리
                String firstBloodLockKey = "firstBloodLock:" + challengeId;
                RLock firstBloodLock = redissonClient.getLock(firstBloodLockKey);
                boolean firstBloodLocked = false;

                try {
                    firstBloodLocked = firstBloodLock.tryLock(5, 5, TimeUnit.SECONDS);
                    if (firstBloodLocked) {
                        long solvedCount = historyRepository.countDistinctByChallengeId(challengeId);
                        if (solvedCount == 1 && !isSignature) {
                            //sendFirstBloodNotification(challenge, user);  //테스트 진행을 위한 퍼블 주석처리
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    if (firstBloodLocked && firstBloodLock.isHeldByCurrentThread()) {
                        firstBloodLock.unlock();
                    }
                }

                // 문제 점수 업데이트 (시그니처는 다이나믹 스코어링 제외)
                if (!isSignature) {
                    updateChallengeScore(challenge);
                }
                challenge.setSolvers(challenge.getSolvers() + 1);
                challengeRepository.save(challenge);

                //팀 점수로 재게산 업데이트 (다이나믹 스코어 반영)
                /*
                log.info("Before running method: recalculateTeamPoints");
                long startTime = System.currentTimeMillis();

                -> 트랜잭션 중첩과 데드락 이슈가 보임. (대기 시간이 10초가 넘어가서 서버에서 504 에러를 반환함)
                이게 문제가 맞는지 테스트용으로 주석처리함.

                //team.ifPresent(this::recalculateTeamPoints);

                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;

                log.info("After running method: recalculateTeamPoints took {} ms ({} seconds)",
                        duration, duration / 1000.0);
                 */

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

    /* 개인별은 모두 주석처리
    // Leaderboard 업데이트 메서드 <- 현재는 사용하지 않음.
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
    
    //유저 개인별 TotalPoins 계산 업데이트
    private void updateUserTotalPointsIndividual(UserEntity user) {
        List<HistoryEntity> userHistoryList = historyRepository.findByLoginIdAndUserDeletedFalseAndChallengeNotDeleted(user.getLoginId());

        LocalDateTime lastSolvedTime = null;
        if (!userHistoryList.isEmpty()) {
            for (HistoryEntity userHistory : userHistoryList) {
                if (lastSolvedTime == null || userHistory.getSolvedTime().isAfter(lastSolvedTime)) {
                    lastSolvedTime = userHistory.getSolvedTime();
                }
            }
        }

        int totalPoints = 0;
        for (HistoryEntity history : userHistoryList) {
            ChallengeEntity challenge = challengeRepository.findById(history.getChallengeId())
                    .orElse(null);
            if (challenge != null) {
                totalPoints += challenge.getPoints();
            }
        }

        user.setTotalPoint(totalPoints);
        userRepository.save(user);

        if (totalPoints > 0) {
            LeaderboardEntity leaderboardEntity = leaderboardRepository.findByLoginId(user.getLoginId())
                    .orElseGet(() -> {
                        LeaderboardEntity newLeaderboard = new LeaderboardEntity();
                        newLeaderboard.setLoginId(user.getLoginId());
                        newLeaderboard.setUniv(user.getUniv());
                        return newLeaderboard;
                    });

            leaderboardEntity.setTotalPoint(totalPoints);
            leaderboardEntity.setLastSolvedTime(lastSolvedTime);
            leaderboardRepository.save(leaderboardEntity);
        } else {
            LeaderboardEntity leaderboardEntity = leaderboardRepository.findByLoginId(user.getLoginId())
                    .orElse(null);
            if (leaderboardEntity != null) {
                leaderboardRepository.delete(leaderboardEntity);
            }
        }
    }   */

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

    /*  //전체 유저 TotalPoints 재계산
    public void updateTotalPoints() {
        log.info("전체 유저 점수 재계산 시작");

        List<String> allUserLoginIds = userRepository.findAllUserLoginIds();

        for(String loginId : allUserLoginIds) {
            UserEntity user = userRepository.findByLoginId(loginId)
                    .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

            List<HistoryEntity> userHistoryList = historyRepository
                    .findByLoginIdAndUserDeletedFalseAndChallengeNotDeleted(loginId);

            int totalPoints = 0;
            LocalDateTime lastSolvedTime = null;

            if (!userHistoryList.isEmpty()) {
                for (HistoryEntity history : userHistoryList) {
                    ChallengeEntity challenge = challengeRepository.findById(history.getChallengeId())
                            .orElse(null);
                    if (challenge != null) {
                        totalPoints += challenge.getPoints();
                    }

                    if (lastSolvedTime == null || history.getSolvedTime().isAfter(lastSolvedTime)) {
                        lastSolvedTime = history.getSolvedTime();
                    }
                }
            }

            user.setTotalPoint(totalPoints);
            userRepository.save(user);

            if (totalPoints > 0) {
                LeaderboardEntity leaderboardEntity = leaderboardRepository.findByLoginId(loginId)
                        .orElseGet(() -> {
                            LeaderboardEntity newLeaderboard = new LeaderboardEntity();
                            newLeaderboard.setLoginId(loginId);
                            newLeaderboard.setUniv(user.getUniv());
                            return newLeaderboard;
                        });

                leaderboardEntity.setTotalPoint(totalPoints);
                leaderboardEntity.setLastSolvedTime(lastSolvedTime);
                leaderboardRepository.save(leaderboardEntity);
            } else {
                int deletedCount = leaderboardRepository.deleteByLoginIdNative(loginId);
                if (deletedCount > 0) {
                    log.debug("유저 {} 리더보드 삭제 완료", loginId);
                }
            }
        }

        log.info("전체 유저 점수 재계산 완료");
    }*/ //유저대신 팀단위로 재계산을 위해 주석처리

    // 전체 팀 점수 재계산
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

        // 2. 메모리에서 계산 (시그니처 문제 제외)
        int totalPoints = 0;
        LocalDateTime lastSolvedTime = null;

        for (Long challengeId : solvedChallengeIds) {
            ChallengeEntity challenge = challengeMap.get(challengeId);
            if (challenge == null) continue;

            // 시그니처 문제는 점수 계산에서 제외
            if (challenge.getCategory() == com.mjsec.ctf.type.ChallengeCategory.SIGNATURE) {
                continue;
            }

            totalPoints += challenge.getPoints();

            Optional<LocalDateTime> latestForThisChallenge = histories.stream()
                    .filter(h -> h.getChallengeId().equals(challengeId))
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

    /*// 개별 팀 즉시 업데이트 (문제 풀이 시) 작성은 했으나 필요없으면 주석처리
    @Transactional
    public void updateTeamPointsImmediate(TeamEntity team, LocalDateTime solvedTime) {
        recalculateTeamPoints(team);
        log.info("팀 {} 점수 즉시 업데이트 완료", team.getTeamName());
    }*/
}
